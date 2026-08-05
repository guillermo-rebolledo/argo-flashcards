package dev.memoji.flashcards.core.generation

import dev.memoji.flashcards.core.data.ApiKeyRepository
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * The one implementation of [CardGenerator]: the Anthropic Messages API, called straight from
 * the device with the user's own key. There is no server in between — see ADR 0002.
 *
 * The shape of what comes back is fixed by a JSON schema the request carries, so the response
 * is guaranteed to parse and there is no "the model wrote something we could not read" path.
 * Everything that can still go wrong is mapped onto a [GenerationFailure], including the model
 * declining, which arrives as a perfectly ordinary HTTP 200.
 */
internal class AnthropicCardGenerator(
    private val apiKeyRepository: ApiKeyRepository,
    private val client: OkHttpClient,
    private val endpoint: String,
) : CardGenerator {

    override suspend fun generate(source: Source): GenerationResult {
        val apiKey = apiKeyRepository.apiKey()
        // Not an error: a fresh install has no key, and the way out of that is Settings.
        if (apiKey.isNullOrBlank()) return GenerationResult.Failed(GenerationFailure.NO_KEY_SET)

        val request = Request.Builder()
            .url(endpoint)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .post(requestBody(source).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        // The response body is read on this dispatcher rather than on whichever thread the
        // caller was on: `await` resumes wherever the coroutine lives, and reading a body
        // blocks. The ViewModel calls this from the main thread.
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).await().use(::readResponse)
            } catch (e: IOException) {
                GenerationResult.Failed(e.asFailure())
            }
        }
    }

    private fun readResponse(response: Response): GenerationResult {
        if (!response.isSuccessful) return GenerationResult.Failed(statusFailure(response.code))

        val message = decodeOrNull<MessageResponse>(response.body.string()) ?: return unexpected
        // Read before anything the model said: a page that never arrived is the reason for
        // whatever came after it, and it is the one failure with its own way forward. Every
        // attempt has to have failed — a retry that worked is a page that was read.
        val fetches = message.content.filter { it.type == FETCH_RESULT_BLOCK }
        if (fetches.isNotEmpty() && fetches.all(ContentBlock::isFailedFetch)) {
            return pageUnreadable
        }
        return when (message.stopReason) {
            // A refusal is an outcome, not an error — it comes back as a 200 like any answer.
            STOP_REFUSAL -> GenerationResult.Failed(GenerationFailure.DECLINED)
            // The answer was cut off part-way, so whatever Cards it holds are incomplete.
            // Shorter material is the way out of it, which is what "nothing usable" says.
            STOP_MAX_TOKENS -> GenerationResult.Failed(GenerationFailure.NO_CARDS)
            else -> message.asResult()
        }
    }

    private fun MessageResponse.asResult(): GenerationResult {
        // Every text block, not the first: a Generation that fetched a page can say what it is
        // about to do before it does it, and the Deck is then the second thing it wrote.
        val deck = content
            .filter { it.type == TEXT_BLOCK }
            .firstNotNullOfOrNull { decodeOrNull<GeneratedDeckPayload>(it.text) }
            ?: return unexpected

        // A paywall, a consent wall, or a page that is only assembled by a browser all fetch
        // perfectly well and arrive as nothing worth reading. The model is the only thing that
        // can tell those apart from thin material, and this is it saying so.
        if (deck.unreadable) return pageUnreadable

        val cards = deck.cards
            .map { GeneratedCard(front = it.front.trim(), back = it.back.trim()) }
            .filter { it.front.isNotEmpty() && it.back.isNotEmpty() }

        // Material too short or too abstract to make Cards out of comes back like this.
        if (cards.isEmpty()) return GenerationResult.Failed(GenerationFailure.NO_CARDS)
        return GenerationResult.Generated(deckName = deck.deckName.trim(), cards = cards)
    }

    /**
     * The schema makes a malformed body unreachable through the API. This is here so that a
     * captive portal answering 200 with a login page cannot take the app down with it.
     */
    private inline fun <reified T> decodeOrNull(body: String?): T? = try {
        body?.let { json.decodeFromString<T>(it) }
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * A URL and a paste are the same request with two differences: what the message says, and
     * whether the fetch tool is on the table. The tool is only offered for a URL, so a paste
     * that mentions a link cannot turn into a page request the user did not ask for.
     */
    private fun requestBody(source: Source): String = json.encodeToString(
        when (source) {
            is Source.PastedText -> request(system = SYSTEM_PROMPT, message = source.text)
            // The URL has to be in the message: the tool only fetches addresses that are
            // already in the conversation, never ones the model has composed.
            is Source.Url -> request(
                system = "$SYSTEM_PROMPT\n\n$PAGE_PROMPT",
                message = "Make cards from the page at ${source.url}",
                tools = listOf(webFetchTool),
            )
        },
    )

    private fun request(
        system: String,
        message: String,
        tools: List<JsonElement>? = null,
    ) = MessageRequest(
        model = MODEL,
        maxTokens = MAX_TOKENS,
        system = system,
        outputConfig = OutputConfig(effort = EFFORT, format = deckFormat),
        messages = listOf(RequestMessage(role = "user", content = message)),
        tools = tools,
    )

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MODEL = "claude-opus-5"

        /**
         * Thinking and the answer come out of the same budget, and the model thinks by
         * default. Eight Cards are a few hundred tokens; the rest of this is headroom, so a
         * long paste is not cut off part-way and reported as material that produced nothing.
         */
        const val MAX_TOKENS = 32_000

        /**
         * Turning a page of notes into eight Cards is close to what the model does unprompted,
         * so this buys a shorter wait rather than better Cards.
         */
        const val EFFORT = "medium"

        const val TEXT_BLOCK = "text"
        const val STOP_REFUSAL = "refusal"
        const val STOP_MAX_TOKENS = "max_tokens"

        /**
         * The page is read by Anthropic, not by the device — see ADR 0005. The basic fetch,
         * rather than the filtering one: filtering trims a page down to what a question needs,
         * and here the whole page is the question. The cap below is what bounds it instead.
         */
        const val WEB_FETCH_TOOL = "web_fetch_20250910"
        const val WEB_FETCH_NAME = "web_fetch"

        /**
         * Roughly a hundred kilobytes of page, which is a long article with room to spare.
         * Past that the tool truncates rather than the request growing without limit: a book
         * behind one URL must not be able to spend a fortune of the user's own money.
         */
        const val MAX_CONTENT_TOKENS = 25_000

        /** One fetch, and one retry for the address that redirects or blinks. */
        const val MAX_FETCHES = 2

        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            // A paste sends no `tools` key at all rather than a null one.
            explicitNulls = false
        }

        val unexpected = GenerationResult.Failed(GenerationFailure.UNEXPECTED)

        val pageUnreadable = GenerationResult.Failed(GenerationFailure.PAGE_UNREADABLE)

        /**
         * The prompt guidance from the design, and nothing about JSON — the schema below is
         * what makes the shape of the answer certain, so the prompt is only about the Cards.
         */
        val SYSTEM_PROMPT = """
            You turn a block of study material into flashcards for someone learning it.

            Write six to eight cards.

            Each card holds one idea, never two. The front is a short term or a direct
            question. The back is one plain sentence of under about eighteen words — no
            lists, no examples, no second sentence.

            Cover what someone studying this would need to recall, not trivia about the text
            itself. Skip anything you cannot state plainly in one sentence.

            Name the deck after the topic in a few words. Write in the language of the material.
        """.trimIndent()

        /**
         * Only sent with a URL. The last line matters most: a page that did not arrive must
         * not become Cards about the address, which is what a model with nothing to read and
         * a schema to fill would otherwise write.
         */
        val PAGE_PROMPT = """
            The material is the page at the link in the message. Read it with the web_fetch
            tool and write the cards from what the page says.

            Some pages arrive without arriving: a login or paywall notice in place of the
            article, a cookie or consent screen, or a shell that says nothing because the page
            is only assembled once a browser runs it. If what came back is one of those, or is
            otherwise not the material it promised, set unreadable to true and write no cards.
            Never guess from the address, and never write cards from what you already know
            about the page — say it could not be read instead.
        """.trimIndent()

        /**
         * The one server-side tool the app uses, and the cap on what it may bring back.
         * Citations stay off — a Card cites nothing, and asking for them alongside a supplied
         * schema is a request the API turns down.
         */
        val webFetchTool: JsonObject = buildJsonObject {
            put("type", WEB_FETCH_TOOL)
            put("name", WEB_FETCH_NAME)
            put("max_uses", MAX_FETCHES)
            put("max_content_tokens", MAX_CONTENT_TOKENS)
        }

        /**
         * A supplied schema, so the response is guaranteed to parse. This replaces the
         * prototype's "find the first {...} and hope" — there is no parse-failure path here.
         */
        val deckFormat: JsonObject = buildJsonObject {
            put("type", "json_schema")
            putJsonObject("schema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("deck_name") {
                        put("type", "string")
                        put("description", "The topic of the material, in a few words.")
                    }
                    // Not required, and never set for a paste: the only material that can
                    // fail to arrive is a page. It is how a paywall — which the fetch itself
                    // calls a success — becomes the failure the user can act on.
                    putJsonObject("unreadable") {
                        put("type", "boolean")
                        put(
                            "description",
                            "True when the page could not be read: a login or paywall notice " +
                                "in place of the material, or a page that only exists once a " +
                                "browser has run it. No cards when this is true.",
                        )
                    }
                    putJsonObject("cards") {
                        put("type", "array")
                        put("description", "Six to eight cards, one idea each.")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("front") {
                                    put("type", "string")
                                    put("description", "A short term or a direct question.")
                                }
                                putJsonObject("back") {
                                    put("type", "string")
                                    put("description", "One plain sentence answering the front.")
                                }
                            }
                            putJsonArray("required") {
                                add("front")
                                add("back")
                            }
                            put("additionalProperties", false)
                        }
                    }
                }
                putJsonArray("required") {
                    add("deck_name")
                    add("cards")
                }
                put("additionalProperties", false)
            }
        }
    }
}

/**
 * The statuses worth telling apart. A rejected key is the one the user is most likely to hit
 * and the one a generic message serves worst: it is the credential that was wrong, not what
 * they pasted.
 */
private fun statusFailure(code: Int): GenerationFailure = when (code) {
    401, 403 -> GenerationFailure.INVALID_KEY
    408, 504 -> GenerationFailure.TIMEOUT
    429 -> GenerationFailure.RATE_LIMITED
    else -> GenerationFailure.UNEXPECTED
}

/**
 * Being offline and timing out both arrive as an [IOException] and need different messages:
 * one is worth retrying now, the other is worth retrying once there is a connection.
 */
private fun IOException.asFailure(): GenerationFailure = when (this) {
    // Covers SocketTimeoutException and OkHttp's own call timeout.
    is InterruptedIOException -> GenerationFailure.TIMEOUT
    is UnknownHostException, is ConnectException, is NoRouteToHostException ->
        GenerationFailure.OFFLINE
    // Everything else that fails mid-flight — a dropped connection, a reset socket. The
    // connection is the thing to look at, which is what the offline message says.
    else -> GenerationFailure.OFFLINE
}

/**
 * Cancels the call when the coroutine is cancelled, which is how leaving the flow stops the
 * request rather than leaving it running against a screen that has gone.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        },
    )
}

@Serializable
private data class MessageRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    @SerialName("output_config") val outputConfig: OutputConfig,
    val messages: List<RequestMessage>,
    /** Omitted rather than sent empty when there is no page to fetch. */
    val tools: List<JsonElement>? = null,
)

@Serializable
private data class OutputConfig(val effort: String, val format: JsonElement)

@Serializable
private data class RequestMessage(val role: String, val content: String)

@Serializable
private data class MessageResponse(
    @SerialName("stop_reason") val stopReason: String? = null,
    val content: List<ContentBlock> = emptyList(),
)

@Serializable
private data class ContentBlock(
    val type: String,
    val text: String? = null,
    /** Present on a tool result: the page, or why there is no page. */
    val content: JsonElement? = null,
)

/**
 * A fetch that did not work comes back as an ordinary part of a successful answer — the tool
 * result carries an error type instead of a document, and the model carries on regardless.
 * Which error it was does not change what the user can do about it, so it is not read.
 */
private fun ContentBlock.isFailedFetch(): Boolean {
    val resultType = (content as? JsonObject)?.get("type") as? JsonPrimitive
    return resultType?.contentOrNull == FETCH_FAILED
}

private const val FETCH_RESULT_BLOCK = "web_fetch_tool_result"
private const val FETCH_FAILED = "web_fetch_tool_result_error"

/** What the schema promises comes back. */
@Serializable
private data class GeneratedDeckPayload(
    @SerialName("deck_name") val deckName: String,
    val cards: List<GeneratedCardPayload> = emptyList(),
    /** Only a page can be unreadable, so a paste never sets this. */
    val unreadable: Boolean = false,
)

@Serializable
private data class GeneratedCardPayload(val front: String, val back: String)
