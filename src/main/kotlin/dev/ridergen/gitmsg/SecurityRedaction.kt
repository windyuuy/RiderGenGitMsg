package dev.ridergen.gitmsg

import java.net.URI

/**
 * Central redaction helpers. Every notification, error message and diagnostic that may
 * contain a token, an Authorization header or a URL must pass through this object.
 */
object SecurityRedaction {

    private val AUTH_HEADER = Regex("(?i)(authorization\\s*[:=]\\s*)([^\\r\\n]+)")
    private val BEARER = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+")
    private val PRIVATE_KEY_BLOCK =
        Regex("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----")
    private val OPENAI_KEY = Regex("\\bsk-[A-Za-z0-9_-]{8,}")
    private val GITHUB_TOKEN = Regex("\\bgh[pousr]_[A-Za-z0-9]{8,}")
    private val AWS_ACCESS_KEY = Regex("\\bAKIA[0-9A-Z]{12,}")
    private val SLACK_TOKEN = Regex("\\bxox[baprs]-[A-Za-z0-9-]{8,}")

    /** Shows only the first and last four characters, e.g. `sk-a••••wxyz`. */
    fun maskToken(token: String): String {
        val value = token.trim()
        if (value.isEmpty()) return ""
        if (value.length <= 8) return "•".repeat(value.length.coerceAtLeast(4))
        return value.take(4) + "••••" + value.takeLast(4)
    }

    /**
     * Removes secrets from arbitrary text. [knownToken] is replaced exactly first, which also
     * covers tokens that do not match any known key format.
     */
    fun redact(text: String, knownToken: String? = null): String {
        var value = text
        val token = knownToken?.trim()
        if (!token.isNullOrEmpty()) {
            value = value.replace(token, "***")
        }
        value = AUTH_HEADER.replace(value) { match -> match.groupValues[1] + "***" }
        value = BEARER.replace(value, "Bearer ***")
        value = PRIVATE_KEY_BLOCK.replace(value, "[REDACTED PRIVATE KEY]")
        value = OPENAI_KEY.replace(value, "sk-***")
        value = GITHUB_TOKEN.replace(value) { match ->
            match.value.substringBefore('_') + "_***"
        }
        value = AWS_ACCESS_KEY.replace(value, "AKIA***")
        value = SLACK_TOKEN.replace(value) { match ->
            match.value.substringBefore('-') + "-***"
        }
        return value
    }

    /** Strips `user:password@` from a URL before showing it in messages. */
    fun sanitizeUrl(raw: String): String {
        val value = raw.trim()
        return runCatching {
            val uri = URI(value)
            if (uri.userInfo == null) {
                value
            } else {
                URI(
                    uri.scheme,
                    null,
                    uri.host,
                    uri.port,
                    uri.path,
                    uri.query,
                    uri.fragment
                ).toString()
            }
        }.getOrDefault(value)
    }
}
