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
import kotlinx.serialization.json.add
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

        val text = when (source) {
            is Source.PastedText -> source.text
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .post(requestBody(text).toRequestBody(JSON_MEDIA_TYPE))
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
        val json = content.firstOrNull { it.type == TEXT_BLOCK }?.text ?: return unexpected
        val deck = decodeOrNull<GeneratedDeckPayload>(json) ?: return unexpected

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

    private fun requestBody(text: String): String = json.encodeToString(
        MessageRequest(
            model = MODEL,
            maxTokens = MAX_TOKENS,
            system = SYSTEM_PROMPT,
            outputConfig = OutputConfig(effort = EFFORT, format = deckFormat),
            messages = listOf(RequestMessage(role = "user", content = text)),
        ),
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

        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        val unexpected = GenerationResult.Failed(GenerationFailure.UNEXPECTED)

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
private data class ContentBlock(val type: String, val text: String? = null)

/** What the schema promises comes back. */
@Serializable
private data class GeneratedDeckPayload(
    @SerialName("deck_name") val deckName: String,
    val cards: List<GeneratedCardPayload> = emptyList(),
)

@Serializable
private data class GeneratedCardPayload(val front: String, val back: String)
