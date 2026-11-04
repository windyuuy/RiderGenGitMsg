package dev.ridergen.gitmsg

/**
 * Scans a diff for high-confidence secret shapes before it is sent to the model.
 *
 * Default behaviour is to redact (mask) every match and tell the user how many were masked.
 * The rules are intentionally narrow to avoid mangling ordinary source code:
 * provider-specific key prefixes and PEM private key blocks.
 */
object SensitiveContentScanner {

    data class Finding(val kind: String, val count: Int)

    data class Result(val text: String, val findings: List<Finding>) {
        val total: Int get() = findings.sumOf { it.count }
        val hasFindings: Boolean get() = findings.isNotEmpty()
    }

    private data class Rule(
        val kind: String,
        val regex: Regex,
        val replacement: (MatchResult) -> String
    )

    private val rules = listOf(
        Rule(
            "PRIVATE_KEY",
            Regex("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----"),
            { "[REDACTED PRIVATE KEY]" }
        ),
        Rule(
            "OPENAI_API_KEY",
            Regex("\\bsk-[A-Za-z0-9_-]{8,}"),
            { "sk-[REDACTED]" }
        ),
        Rule(
            "GITHUB_TOKEN",
            Regex("\\bgh[pousr]_[A-Za-z0-9]{8,}"),
            { match -> match.value.substringBefore('_') + "_[REDACTED]" }
        ),
        Rule(
            "AWS_ACCESS_KEY",
            Regex("\\bAKIA[0-9A-Z]{12,}"),
            { "AKIA[REDACTED]" }
        ),
        Rule(
            "SLACK_TOKEN",
            Regex("\\bxox[baprs]-[A-Za-z0-9-]{8,}"),
            { match -> match.value.substringBefore('-') + "-[REDACTED]" }
        ),
        Rule(
            "GENERIC_SECRET",
            Regex(
                "(?i)(\\b(?:api[_-]?key|secret|password|token)\\b\\s*[:=]\\s*)" +
                    "([\"']?)([A-Za-z0-9_./+=-]{16,})\\2"
            ),
            { match ->
                val prefix = match.groupValues[1]
                val quote = match.groupValues[2]
                prefix + quote + "[REDACTED]" + quote
            }
        )
    )

    fun scan(text: String): Result {
        var current = text
        val findings = mutableListOf<Finding>()
        for (rule in rules) {
            var count = 0
            current = rule.regex.replace(current) { match ->
                count += 1
                rule.replacement(match)
            }
            if (count > 0) findings += Finding(rule.kind, count)
        }
        return Result(current, findings)
    }

    fun redact(text: String): String = scan(text).text
}
