package dev.ridergen.gitmsg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveContentScannerTest {

    @Test
    fun `clean diff is returned unchanged`() {
        val diff = "+ fun parse() = Unit\n- val old = 1"
        val result = SensitiveContentScanner.scan(diff)
        assertFalse(result.hasFindings)
        assertEquals(diff, result.text)
    }

    @Test
    fun `provider key prefixes are masked by default`() {
        val result = SensitiveContentScanner.scan(
            "+ val key = \"sk-abcdefghijklmnop\"\n+ token ghp_abcdefghijklmnopqrstuv"
        )
        assertTrue(result.hasFindings)
        assertFalse(result.text.contains("sk-abcdefghijklmnop"), result.text)
        assertFalse(result.text.contains("ghp_abcdefghijklmnopqrstuv"), result.text)
        assertTrue(result.text.contains("sk-[REDACTED]"), result.text)
        assertTrue(result.text.contains("ghp_[REDACTED]"), result.text)
    }

    @Test
    fun `aws keys and private key blocks are masked`() {
        val text = """
            + aws AKIAABCDEFGHIJKLMNOP
            + -----BEGIN RSA PRIVATE KEY-----
            + MIIEowIBAAKCAQEA
            + -----END RSA PRIVATE KEY-----
        """.trimIndent()
        val result = SensitiveContentScanner.scan(text)
        assertTrue(result.hasFindings)
        assertFalse(result.text.contains("AKIAABCDEFGHIJKLMNOP"), result.text)
        assertFalse(result.text.contains("MIIEowIBAAKCAQEA"), result.text)
        assertTrue(result.text.contains("AKIA[REDACTED]"), result.text)
        assertTrue(result.text.contains("[REDACTED PRIVATE KEY]"), result.text)
    }

    @Test
    fun `secret assignments keep the field name`() {
        val result = SensitiveContentScanner.scan("+ password = \"hfsecretvalue1234567890\"")
        assertTrue(result.hasFindings)
        assertFalse(result.text.contains("hfsecretvalue1234567890"), result.text)
        assertTrue(result.text.contains("password = \"[REDACTED]\""), result.text)
    }
}
