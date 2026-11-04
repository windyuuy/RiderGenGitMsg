package dev.ridergen.gitmsg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityRedactionTest {

    @Test
    fun `known token is removed verbatim`() {
        val text = "request failed for token abc123XYZ still valid"
        val redacted = SecurityRedaction.redact(text, "abc123XYZ")
        assertFalse(redacted.contains("abc123XYZ"), redacted)
        assertTrue(redacted.contains("***"), redacted)
    }

    @Test
    fun `authorization headers are redacted`() {
        val redacted = SecurityRedaction.redact("Authorization: Bearer sk-1234567890abcdef")
        assertFalse(redacted.contains("sk-1234567890abcdef"), redacted)
        assertFalse(redacted.contains("Bearer sk-"), redacted)
        assertTrue(redacted.contains("Authorization:"), redacted)
    }

    @Test
    fun `bearer tokens are redacted`() {
        val redacted = SecurityRedaction.redact("sent with Bearer abcdefghijklmnopqrstuv")
        assertFalse(redacted.contains("abcdefghijklmnopqrstuv"), redacted)
        assertTrue(redacted.contains("Bearer ***"), redacted)
    }

    @Test
    fun `api key shapes are redacted`() {
        val sk = SecurityRedaction.redact("key=sk-abcdefghijklmnop")
        assertFalse(sk.contains("sk-abcdefghijklmnop"), sk)
        assertTrue(sk.contains("sk-***"), sk)

        val github = SecurityRedaction.redact("token ghp_abcdefghijklmnopqrstuv")
        assertFalse(github.contains("ghp_abcdefghijklmnopqrstuv"), github)
        assertTrue(github.contains("ghp_***"), github)

        val aws = SecurityRedaction.redact("aws AKIAABCDEFGHIJKLMNOP")
        assertFalse(aws.contains("AKIAABCDEFGHIJKLMNOP"), aws)
        assertTrue(aws.contains("AKIA***"), aws)
    }

    @Test
    fun `private key blocks are redacted`() {
        val text = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEowIBAAKCAQEA
            -----END RSA PRIVATE KEY-----
        """.trimIndent()
        val redacted = SecurityRedaction.redact(text)
        assertFalse(redacted.contains("MIIEowIBAAKCAQEA"), redacted)
        assertTrue(redacted.contains("[REDACTED PRIVATE KEY]"), redacted)
    }

    @Test
    fun `url userinfo is stripped from messages`() {
        val sanitized = SecurityRedaction.sanitizeUrl("https://user:secret@api.deepseek.com/v1")
        assertFalse(sanitized.contains("user"), sanitized)
        assertFalse(sanitized.contains("secret"), sanitized)
        assertTrue(sanitized.contains("api.deepseek.com"), sanitized)
    }

    @Test
    fun `mask keeps the first and last four characters`() {
        assertEquals("sk-1••••cdef", SecurityRedaction.maskToken("sk-1234567890abcdef"))
        assertEquals("••••", SecurityRedaction.maskToken("abc"))
    }
}
