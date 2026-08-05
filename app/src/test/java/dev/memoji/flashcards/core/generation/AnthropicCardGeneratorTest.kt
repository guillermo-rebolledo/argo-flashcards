package dev.memoji.flashcards.core.generation

import dev.memoji.flashcards.core.data.FakeApiKeyRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one place the app talks to a network, tested against a server that answers exactly what
 * the API would. What is being pinned down is the mapping: which answer becomes which typed
 * failure, and that a refusal — an ordinary HTTP 200 — is read as an outcome rather than a win.
 */
class AnthropicCardGeneratorTest {

    private val server = MockWebServer()
    private val apiKeyRepository = FakeApiKeyRepository("sk-ant-secret")

    private val client = OkHttpClient.Builder()
        .readTimeout(1, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.SECONDS)
        .build()

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `a generated Deck comes back as its name and its Cards`() = runTest {
        server.enqueue(success(DECK_JSON))

        val result = generate()

        val generated = result as GenerationResult.Generated
        assertEquals("Big-O notation", generated.deckName)
        assertEquals(
            listOf(GeneratedCard("O(1)", "Constant time."), GeneratedCard("O(n)", "Linear time.")),
            generated.cards,
        )
    }

    @Test
    fun `the request carries the key, the pasted text and the schema`() = runTest {
        server.enqueue(success(DECK_JSON))

        generate("Big-O notation describes how work grows.")

        val request = server.takeRequest()
        assertEquals("sk-ant-secret", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("Big-O notation describes how work grows."))
        assertTrue(body.contains("json_schema"))
        assertTrue(body.contains("deck_name"))
    }

    /** The page is read by Anthropic, so the URL and the tool that reads it both go up. */
    @Test
    fun `a URL is sent with the fetch tool and a cap on what it may bring back`() = runTest {
        server.enqueue(success(DECK_JSON))

        generate(Source.Url("https://example.com/big-o"))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("https://example.com/big-o"))
        assertTrue(body.contains("web_fetch"))
        assertTrue(body.contains("max_content_tokens"))
    }

    /** A paste that mentions a link must not become a request to go and read it. */
    @Test
    fun `pasted text is sent with no tools at all`() = runTest {
        server.enqueue(success(DECK_JSON))

        generate("Read https://example.com for more.")

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("tools"))
    }

    @Test
    fun `a page that was read produces its Cards`() = runTest {
        server.enqueue(success(DECK_JSON))

        val generated = generate(Source.Url("https://example.com/big-o"))

        assertEquals("Big-O notation", (generated as GenerationResult.Generated).deckName)
    }

    /** A Generation that went and read something can say so before it writes the Deck. */
    @Test
    fun `a Deck is found past whatever was said before it`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"stop_reason":"end_turn","content":[""" +
                    """{"type":"text","text":"I will read that page."},""" +
                    """{"type":"web_fetch_tool_result","tool_use_id":"srvtoolu_1",""" +
                    """"content":{"type":"web_fetch_result","url":"https://example.com"}},""" +
                    """{"type":"text","text":"$DECK_JSON"}]}""",
            ),
        )

        val generated = generate(Source.Url("https://example.com/big-o"))

        assertEquals("Big-O notation", (generated as GenerationResult.Generated).deckName)
    }

    /**
     * A fetch that failed is not an error: it comes back inside a perfectly good answer, and
     * the model carries on writing regardless. Reading it is what stops the user being told
     * their material was no good when the truth is the page never arrived.
     */
    @Test
    fun `a page that could not be read says so`() = runTest {
        server.enqueue(fetchError("url_not_accessible"))

        assertEquals(
            failure(GenerationFailure.PAGE_UNREADABLE),
            generate(Source.Url("https://example.com/paywalled")),
        )
    }

    @Test
    fun `a login wall reads the same way as any other unreadable page`() = runTest {
        server.enqueue(fetchError("unsupported_content_type"))

        assertEquals(
            failure(GenerationFailure.PAGE_UNREADABLE),
            generate(Source.Url("https://example.com/login")),
        )
    }

    /** Nothing is sent anywhere until there is a key to send it with. */
    @Test
    fun `no key set never reaches the network`() = runTest {
        apiKeyRepository.clearApiKey()

        val result = generate()

        assertEquals(failure(GenerationFailure.NO_KEY_SET), result)
        assertEquals(0, server.requestCount)
    }

    /** A refusal is a 200: read as a success it would look like a Deck of no Cards. */
    @Test
    fun `a refusal reads as the model declining`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"stop_reason":"refusal","stop_details":{"type":"refusal"},"content":[]}""",
            ),
        )

        assertEquals(failure(GenerationFailure.DECLINED), generate())
    }

    /** Cut off part-way through: whatever it wrote is incomplete, so shorter material it is. */
    @Test
    fun `an answer cut short reads as nothing usable`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"stop_reason":"max_tokens","content":[{"type":"text","text":"{\"deck"}]}""",
            ),
        )

        assertEquals(failure(GenerationFailure.NO_CARDS), generate())
    }

    @Test
    fun `a Deck of no Cards reads as nothing usable`() = runTest {
        server.enqueue(success("""{\"deck_name\":\"Nothing\",\"cards\":[]}"""))

        assertEquals(failure(GenerationFailure.NO_CARDS), generate())
    }

    @Test
    fun `a Card missing a side is dropped`() = runTest {
        server.enqueue(
            success(
                """{\"deck_name\":\"Big-O notation\",\"cards\":[""" +
                    """{\"front\":\"O(1)\",\"back\":\"Constant time.\"},""" +
                    """{\"front\":\"O(n)\",\"back\":\"   \"}]}""",
            ),
        )

        val generated = generate() as GenerationResult.Generated
        assertEquals(listOf(GeneratedCard("O(1)", "Constant time.")), generated.cards)
    }

    @Test
    fun `a rejected key says the key was rejected`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"type":"error"}"""))

        assertEquals(failure(GenerationFailure.INVALID_KEY), generate())
    }

    @Test
    fun `a key without permission also says the key was rejected`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        assertEquals(failure(GenerationFailure.INVALID_KEY), generate())
    }

    @Test
    fun `too many requests says so`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        assertEquals(failure(GenerationFailure.RATE_LIMITED), generate())
    }

    @Test
    fun `a server error is not blamed on the key or the text`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(failure(GenerationFailure.UNEXPECTED), generate())
    }

    @Test
    fun `an answer that never arrives times out`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertEquals(failure(GenerationFailure.TIMEOUT), generate())
    }

    /** Nothing listening on the other end is what being offline looks like from here. */
    @Test
    fun `a connection that cannot be made reads as offline`() = runTest {
        val endpoint = server.url("/v1/messages").toString()
        server.shutdown()

        val result = AnthropicCardGenerator(apiKeyRepository, client, endpoint)
            .generate(Source.PastedText("Big-O notation."))

        assertEquals(failure(GenerationFailure.OFFLINE), result)
    }

    /** A captive portal answering 200 with a login page must not take the app down. */
    @Test
    fun `a body that is not the API is not a crash`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>Sign in</html>"))

        assertEquals(failure(GenerationFailure.UNEXPECTED), generate())
    }

    private suspend fun generate(text: String = "Big-O notation.") =
        generate(Source.PastedText(text))

    private suspend fun generate(source: Source) =
        AnthropicCardGenerator(apiKeyRepository, client, server.url("/v1/messages").toString())
            .generate(source)

    private fun failure(failure: GenerationFailure) = GenerationResult.Failed(failure)

    /** What a fetch that did not work looks like: a 200, with the reason inside the answer. */
    private fun fetchError(code: String) = MockResponse()
        .setResponseCode(200)
        .setBody(
            """{"stop_reason":"end_turn","content":[""" +
                """{"type":"web_fetch_tool_result","tool_use_id":"srvtoolu_1",""" +
                """"content":{"type":"web_fetch_tool_result_error","error_code":"$code"}},""" +
                """{"type":"text","text":"I could not read that page."}]}""",
        )

    /** What the API returns: the Deck as JSON, inside a text block, inside the message. */
    private fun success(deckJson: String) = MockResponse()
        .setResponseCode(200)
        .setBody("""{"stop_reason":"end_turn","content":[{"type":"text","text":"$deckJson"}]}""")

    private companion object {
        val DECK_JSON = """{\"deck_name\":\"Big-O notation\",\"cards\":[""" +
            """{\"front\":\"O(1)\",\"back\":\"Constant time.\"},""" +
            """{\"front\":\"O(n)\",\"back\":\"Linear time.\"}]}"""
    }
}
