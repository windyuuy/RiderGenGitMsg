package dev.ridergen.gitmsg

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeepSeekUrlPolicyTest {

    private fun rejected(raw: String, confirmed: Boolean = false, localhost: Boolean = false): Boolean =
        DeepSeekUrlPolicy.validate(raw, confirmed, localhost) is DeepSeekUrlPolicy.Result.Rejected

    @Test
    fun `official default is allowed`() {
        assertFalse(rejected(RiderGenSettings.DEFAULT_BASE_URL))
    }

    @Test
    fun `http is rejected for remote hosts`() {
        assertTrue(rejected("http://api.deepseek.com"))
        assertTrue(rejected("http://example.com"))
    }

    @Test
    fun `userinfo is rejected`() {
        assertTrue(rejected("https://user:pass@api.deepseek.com"))
    }

    @Test
    fun `ports other than 443 are rejected`() {
        assertTrue(rejected("https://api.deepseek.com:8443"))
        assertTrue(rejected("https://api.deepseek.com:80"))
        assertFalse(rejected("https://api.deepseek.com:443"))
    }

    @Test
    fun `custom hosts require explicit confirmation`() {
        assertTrue(rejected("https://example.com/v1"))
        assertFalse(rejected("https://example.com/v1", confirmed = true))
    }

    @Test
    fun `localhost http needs the debug switch`() {
        assertTrue(rejected("http://127.0.0.1:8080"))
        assertTrue(rejected("http://localhost:8080"))
        assertFalse(rejected("http://127.0.0.1:8080", localhost = true))
        assertFalse(rejected("http://localhost:8080", localhost = true))
    }

    @Test
    fun `invalid urls are rejected`() {
        assertTrue(rejected("not a url"))
        assertTrue(rejected(""))
        assertTrue(rejected("https://"))
    }

    @Test
    fun `custom host detection matches the official host only`() {
        assertFalse(DeepSeekUrlPolicy.isCustomHost("https://api.deepseek.com"))
        assertFalse(DeepSeekUrlPolicy.isCustomHost("https://api.deepseek.com/v1"))
        assertTrue(DeepSeekUrlPolicy.isCustomHost("https://example.com"))
        assertTrue(DeepSeekUrlPolicy.isCustomHost("http://127.0.0.1:8080"))
    }
}
