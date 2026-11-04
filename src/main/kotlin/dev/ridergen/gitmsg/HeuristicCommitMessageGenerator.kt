package dev.ridergen.gitmsg

import dev.ridergen.gitmsg.ChangedFile.ChangeKind

/**
 * Deterministic, offline commit-message generator based on the list of staged files.
 * Deliberately free of IntelliJ Platform dependencies so it can be unit-tested directly.
 */
object HeuristicCommitMessageGenerator {

    fun generate(files: List<ChangedFile>, stat: String, conventional: Boolean): String {
        if (files.isEmpty()) {
            return if (conventional) "chore: no staged changes" else "No staged changes"
        }

        val subject = summarize(files)
        val body = buildBody(files, stat)

        return if (conventional) {
            val type = inferType(files)
            val scope = inferScope(files)
            val header = if (scope != null) "$type($scope): $subject" else "$type: $subject"
            if (body.isBlank()) header else "$header\n\n$body"
        } else {
            val header = subject.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            if (body.isBlank()) header else "$header\n\n$body"
        }
    }

    internal fun inferType(files: List<ChangedFile>): String {
        val paths = files.map { normalize(it.path) }
        if (paths.all(::isDocs)) return "docs"
        if (paths.all(::isTest)) return "test"
        if (paths.all(::isBuild)) return "build"
        if (paths.all(::isCi)) return "ci"
        if (files.all { it.kind == ChangeKind.DELETED }) return "chore"
        if (files.all { it.kind == ChangeKind.MODIFIED || it.kind == ChangeKind.RENAMED }) return "refactor"
        if (files.any { it.kind == ChangeKind.ADDED }) return "feat"
        return "chore"
    }

    internal fun inferScope(files: List<ChangedFile>): String? {
        val segments = files.map { normalize(it.path).split('/').filter(String::isNotBlank) }
        if (segments.any { it.isEmpty() }) return null

        val common = if (segments.size == 1) {
            segments.first().dropLast(1)
        } else {
            var prefix = segments.first()
            for (current in segments.drop(1)) {
                var index = 0
                while (index < prefix.size && index < current.size && prefix[index] == current[index]) {
                    index++
                }
                prefix = prefix.take(index)
            }
            prefix
        }

        return common.lastOrNull()?.takeIf { it.length <= 24 }
    }

    internal fun summarize(files: List<ChangedFile>): String {
        val verb = when {
            files.all { it.kind == ChangeKind.ADDED } -> "add"
            files.all { it.kind == ChangeKind.DELETED } -> "remove"
            files.any { it.kind == ChangeKind.ADDED } -> "add"
            else -> "update"
        }

        val subject = if (files.size == 1) {
            "$verb ${fileName(files.first().path)}"
        } else {
            val names = files.take(3).joinToString(", ") { fileName(it.path) }
            val more = if (files.size > 3) ", ..." else ""
            "$verb ${files.size} files: $names$more"
        }
        return truncate(subject, 72)
    }

    internal fun truncate(value: String, maxLength: Int): String {
        if (value.length <= maxLength) return value
        val keep = (maxLength - 3).coerceAtLeast(0)
        return value.take(keep) + "..."
    }

    private fun buildBody(files: List<ChangedFile>, stat: String): String {
        val lines = mutableListOf<String>()
        lines += "Changed files:"

        val grouped = files.groupBy { it.status.firstOrNull()?.uppercaseChar() ?: '?' }
        for ((status, group) in grouped) {
            lines += "- ${statusLabel(status)} (${group.size}):"
            group.take(20).forEach { lines += "  - ${it.path}" }
            if (group.size > 20) {
                lines += "  - ... and ${group.size - 20} more"
            }
        }

        val trimmedStat = stat.trim()
        if (trimmedStat.isNotEmpty()) {
            lines += ""
            lines += truncate(trimmedStat, 2000)
        }
        return lines.joinToString("\n")
    }

    private fun statusLabel(status: Char): String = when (status) {
        'A' -> "Added"
        'M' -> "Modified"
        'D' -> "Deleted"
        'R' -> "Renamed"
        'C' -> "Copied"
        else -> "Other"
    }

    private fun fileName(path: String): String =
        path.replace('\\', '/').substringAfterLast('/').ifBlank { path }

    private fun normalize(path: String): String = path.replace('\\', '/').lowercase()

    private fun isDocs(path: String): Boolean =
        path.contains("readme") ||
            path.contains("changelog") ||
            path.endsWith(".md") ||
            path.endsWith(".rst") ||
            path.startsWith("docs/") ||
            path.startsWith("doc/") ||
            path.contains("/docs/") ||
            path.contains("/doc/")

    private fun isTest(path: String): Boolean =
        path.contains("/test/") ||
            path.contains("/tests/") ||
            path.startsWith("test/") ||
            path.startsWith("tests/") ||
            path.endsWith("test.kt") ||
            path.endsWith("tests.cs") ||
            path.endsWith("_test.go") ||
            path.endsWith(".spec.ts") ||
            path.endsWith(".test.ts") ||
            path.endsWith(".spec.tsx") ||
            path.endsWith(".test.tsx")

    private fun isBuild(path: String): Boolean =
        path.endsWith(".gradle") ||
            path.endsWith(".gradle.kts") ||
            path.endsWith("pom.xml") ||
            path.endsWith("package.json") ||
            path.endsWith(".csproj") ||
            path.endsWith(".sln") ||
            path.endsWith("directory.build.props") ||
            path.endsWith("gradle.properties") ||
            path.endsWith("go.mod") ||
            path.endsWith("cargo.toml")

    private fun isCi(path: String): Boolean =
        path.startsWith(".github/") ||
            path.startsWith(".gitlab/") ||
            path.contains(".gitlab-ci") ||
            path.contains("azure-pipelines") ||
            path.contains("jenkinsfile") ||
            path.startsWith(".circleci/")
}
