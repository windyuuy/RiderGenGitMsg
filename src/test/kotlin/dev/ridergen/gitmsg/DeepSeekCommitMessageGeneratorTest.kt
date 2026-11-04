package dev.ridergen.gitmsg

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeepSeekCommitMessageGeneratorTest {

    private fun startServer(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
        server.start()
        return server
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private val snapshot = StagedSnapshot(
        files = listOf(ChangedFile("A", "src/Parser.kt")),
        diff = "+ fun parse() = Unit",
        stat = " src/Parser.kt | 1 +"
    )

    @Test
    fun `parses a deepseek chat completions response`() {
        val server = startServer { exchange ->
            respond(
                exchange,
                200,
                """{"choices":[{"message":{"role":"assistant","content":"feat(parser): add staged diff support"}}]}"""
            )
        }
        try {
            val settings = RiderGenSettings.State(baseUrl = "http://127.0.0.1:${server.address.port}")
            val message = DeepSeekCommitMessageGenerator.generate(settings, snapshot, "sk-test")
            assertEquals("feat(parser): add staged diff support", message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `token probe returns the model list on success`() {
        val server = startServer { exchange ->
            respond(
                exchange,
                200,
                """{"object":"list","data":[{"id":"deepseek-chat"},{"id":"deepseek-reasoner"}]}"""
            )
        }
        try {
            val result = DeepSeekCommitMessageGenerator.testToken(
                "http://127.0.0.1:${server.address.port}",
                "sk-test"
            )
            assertTrue(result.ok, result.message)
            assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), result.models)
            assertTrue(result.message.contains("Token 有效"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `401 is reported as an invalid token`() {
        val server = startServer { exchange ->
            respond(
                exchange,
                401,
                """{"error":{"message":"Authentication Fails (no such user)","type":"authentication_error"}}"""
            )
        }
        try {
            val result = DeepSeekCommitMessageGenerator.testToken(
                "http://127.0.0.1:${server.address.port}",
                "sk-bad"
            )
            assertFalse(result.ok)
            assertEquals(DeepSeekErrorKind.AUTH, result.errorKind)
            assertTrue(result.message.contains("401"), result.message)
            assertTrue(result.message.contains("Token"), result.message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `402 is reported as insufficient balance`() {
        val server = startServer { exchange ->
            respond(
                exchange,
                402,
                """{"error":{"message":"Insufficient Balance","code":"invalid_request_error"}}"""
            )
        }
        try {
            val result = DeepSeekCommitMessageGenerator.testToken(
                "http://127.0.0.1:${server.address.port}",
                "sk-empty"
            )
            assertFalse(result.ok)
            assertEquals(DeepSeekErrorKind.QUOTA, result.errorKind)
            assertTrue(result.message.contains("余额不足"), result.message)
            assertTrue(result.message.contains("Insufficient Balance"), result.message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `429 is reported as rate limiting`() {
        val server = startServer { exchange ->
            respond(
                exchange,
                429,
                """{"error":{"message":"Rate limit reached","type":"rate_limit_error"}}"""
            )
        }
        try {
            val result = DeepSeekCommitMessageGenerator.testToken(
                "http://127.0.0.1:${server.address.port}",
                "sk-test"
            )
            assertFalse(result.ok)
            assertEquals(DeepSeekErrorKind.RATE_LIMIT, result.errorKind)
            assertTrue(result.message.contains("限流"), result.message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `unreachable host is reported as a network error`() {
        val server = startServer { exchange -> respond(exchange, 200, "{}") }
        val port = server.address.port
        server.stop(0)

        val result = DeepSeekCommitMessageGenerator.testToken("http://127.0.0.1:$port", "sk-test")
        assertFalse(result.ok)
        assertEquals(DeepSeekErrorKind.NETWORK, result.errorKind)
        assertTrue(result.message.contains("网络不可达"), result.message)
    }

    @Test
    fun `non json success body is reported as an invalid response`() {
        val server = startServer { exchange -> respond(exchange, 200, "<html>not json</html>") }
        try {
            val result = DeepSeekCommitMessageGenerator.testToken(
                "http://127.0.0.1:${server.address.port}",
                "sk-test"
            )
            assertFalse(result.ok)
            assertEquals(DeepSeekErrorKind.INVALID_RESPONSE, result.errorKind)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `missing choices in chat response is rejected`() {
        val server = startServer { exchange -> respond(exchange, 200, """{"choices":[]}""") }
        try {
            val settings = RiderGenSettings.State(baseUrl = "http://127.0.0.1:${server.address.port}")
            val error = assertFailsWith<DeepSeekException> {
                DeepSeekCommitMessageGenerator.generate(settings, snapshot, "sk-test")
            }
            assertEquals(DeepSeekErrorKind.INVALID_RESPONSE, error.kind)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `http failure descriptions distinguish status codes`() {
        assertTrue(DeepSeekCommitMessageGenerator.describeHttpFailure(403, "").contains("Token 无效"))
        assertTrue(DeepSeekCommitMessageGenerator.describeHttpFailure(402, "").contains("余额不足"))
        assertTrue(DeepSeekCommitMessageGenerator.describeHttpFailure(429, "").contains("限流"))
        assertTrue(DeepSeekCommitMessageGenerator.describeHttpFailure(503, "").contains("503"))
        assertTrue(DeepSeekCommitMessageGenerator.describeHttpFailure(418, "").contains("418"))
    }

    @Test
    fun `chat and models endpoints are normalized`() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            DeepSeekCommitMessageGenerator.endpoint("https://example.com/v1/")
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            DeepSeekCommitMessageGenerator.endpoint("https://example.com/v1/chat/completions")
        )
        assertEquals(
            "https://api.deepseek.com/models",
            DeepSeekCommitMessageGenerator.modelsEndpoint("https://api.deepseek.com")
        )
        assertEquals(
            "https://api.deepseek.com/models",
            DeepSeekCommitMessageGenerator.modelsEndpoint("https://api.deepseek.com/chat/completions")
        )
    }
}
