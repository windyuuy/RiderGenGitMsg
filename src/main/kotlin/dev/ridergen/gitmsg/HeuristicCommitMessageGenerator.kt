package dev.ridergen.gitmsg

import dev.ridergen.gitmsg.ChangedFile.ChangeKind

/**
 * Deterministic, offline commit-message generator based on the list of staged files.
 * Deliberately free of IntelliJ Platform dependencies so it can be unit-tested directly.
 */
object HeuristicCommitMessageGenerator {

    fun generate(
        files: List<ChangedFile>,
        stat: String,
        conventional: Boolean,
        responseLanguage: String = "en",
        customInstructions: String = ""
    ): String {
        val preferences = resolvePreferences(conventional, responseLanguage, customInstructions)

        if (files.isEmpty()) {
            return when {
                preferences.supportConventional && preferences.chinese -> "chore: 没有已暂存改动"
                preferences.supportConventional -> "chore: no staged changes"
                preferences.chinese -> "没有已暂存改动"
                else -> "No staged changes"
            }
        }

        val subject = summarize(files, preferences.chinese)
        val body = if (preferences.includeBody) buildBody(files, stat, preferences.chinese) else ""

        return if (preferences.supportConventional) {
            val type = inferType(files)
            val scope = if (preferences.includeScope) inferScope(files) else null
            val header = if (scope != null) "$type($scope): $subject" else "$type: $subject"
            if (body.isBlank()) header else "$header\n\n$body"
        } else {
            val header = if (preferences.chinese) {
                subject
            } else {
                subject.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            if (body.isBlank()) header else "$header\n\n$body"
        }
    }

    private data class Preferences(
        val supportConventional: Boolean,
        val chinese: Boolean,
        val includeBody: Boolean,
        val includeScope: Boolean
    )

    /**
     * Best-effort interpretation of free-form custom instructions for the offline fallback.
     * The AI path sends the raw rules to DeepSeek; here we only honour explicit language,
     * Conventional-prefix, scope and body intent so the fallback stays reasonably aligned.
     */
    private fun resolvePreferences(
        conventional: Boolean,
        responseLanguage: String,
        customInstructions: String
    ): Preferences {
        val text = customInstructions.lowercase()

        val chinese = when {
            containsAny(text, "简体中文", "中文", "汉语", "chinese") -> true
            containsAny(text, "english", "英文", "英语") -> false
            else -> !responseLanguage.equals("en", ignoreCase = true)
        }

        val supportConventional = when {
            containsAny(
                text,
                "不使用 conventional",
                "不要 conventional",
                "不加前缀",
                "不要前缀",
                "without prefix",
                "no prefix",
                "non-conventional",
                "plain message"
            ) -> false

            containsAny(text, "使用 conventional", "use conventional", "must use conventional", "必须 conventional") -> true
            else -> conventional
        }

        val includeBody = !containsAny(
            text,
            "不要 body",
            "不要正文",
            "no body",
            "without body",
            "only a subject",
            "subject only",
            "仅首行",
            "只要标题"
        )

        val includeScope = !containsAny(text, "不要 scope", "不要scope", "no scope", "without scope")

        return Preferences(supportConventional, chinese, includeBody, includeScope)
    }

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it) }

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

    internal fun summarize(files: List<ChangedFile>, chinese: Boolean = false): String {
        val verb = when {
            files.all { it.kind == ChangeKind.ADDED } -> if (chinese) "新增" else "add"
            files.all { it.kind == ChangeKind.DELETED } -> if (chinese) "删除" else "remove"
            files.any { it.kind == ChangeKind.ADDED } -> if (chinese) "新增" else "add"
            else -> if (chinese) "更新" else "update"
        }

        val subject = if (files.size == 1) {
            "$verb ${fileName(files.first().path)}"
        } else {
            val names = files.take(3).joinToString(", ") { fileName(it.path) }
            val more = if (files.size > 3) ", ..." else ""
            if (chinese) {
                "$verb ${files.size} 个文件: $names$more"
            } else {
                "$verb ${files.size} files: $names$more"
            }
        }
        return truncate(subject, 72)
    }

    internal fun truncate(value: String, maxLength: Int): String {
        if (value.length <= maxLength) return value
        val keep = (maxLength - 3).coerceAtLeast(0)
        return value.take(keep) + "..."
    }

    private fun buildBody(files: List<ChangedFile>, stat: String, chinese: Boolean): String {
        val lines = mutableListOf<String>()
        lines += if (chinese) "变更文件:" else "Changed files:"

        val grouped = files.groupBy { it.status.firstOrNull()?.uppercaseChar() ?: '?' }
        for ((status, group) in grouped) {
            lines += if (chinese) {
                "- ${statusLabel(status, true)} (${group.size} 个):"
            } else {
                "- ${statusLabel(status, false)} (${group.size}):"
            }
            group.take(20).forEach { lines += "  - ${it.path}" }
            if (group.size > 20) {
                lines += if (chinese) "  - ... 以及另外 ${group.size - 20} 个" else "  - ... and ${group.size - 20} more"
            }
        }

        val trimmedStat = stat.trim()
        if (trimmedStat.isNotEmpty()) {
            lines += ""
            lines += truncate(trimmedStat, 2000)
        }
        return lines.joinToString("\n")
    }

    private fun statusLabel(status: Char, chinese: Boolean): String = when (status) {
        'A' -> if (chinese) "新增" else "Added"
        'M' -> if (chinese) "修改" else "Modified"
        'D' -> if (chinese) "删除" else "Deleted"
        'R' -> if (chinese) "重命名" else "Renamed"
        'C' -> if (chinese) "复制" else "Copied"
        else -> if (chinese) "其他" else "Other"
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
