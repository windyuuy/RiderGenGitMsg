package dev.ridergen.gitmsg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeuristicCommitMessageGeneratorTest {

    @Test
    fun `added source file uses feat type and scope`() {
        val message = HeuristicCommitMessageGenerator.generate(
            listOf(ChangedFile("A", "game/Player.cs")),
            " game/Player.cs | 10 ++++++++++",
            conventional = true
        )

        val header = message.lineSequence().first()
        assertTrue(header.startsWith("feat("), "unexpected header: $header")
        assertTrue(header.contains("add Player.cs"), "unexpected header: $header")
        assertTrue(message.contains("Changed files:"))
    }

    @Test
    fun `documentation-only change uses docs type`() {
        val message = HeuristicCommitMessageGenerator.generate(
            listOf(ChangedFile("M", "README.md")),
            " README.md | 2 ++",
            conventional = true
        )

        assertEquals("docs: update README.md", message.lineSequence().first())
    }

    @Test
    fun `plain style has no conventional prefix`() {
        val message = HeuristicCommitMessageGenerator.generate(
            listOf(ChangedFile("M", "README.md")),
            "",
            conventional = false
        )

        assertTrue(message.lineSequence().first().startsWith("Update README.md"))
    }

    @Test
    fun `empty staged set is handled`() {
        assertEquals(
            "chore: no staged changes",
            HeuristicCommitMessageGenerator.generate(emptyList(), "", conventional = true)
        )
    }

    @Test
    fun `multiple added files are summarized`() {
        val message = HeuristicCommitMessageGenerator.generate(
            listOf(
                ChangedFile("A", "src/main/kotlin/A.kt"),
                ChangedFile("A", "src/main/kotlin/B.kt"),
                ChangedFile("A", "src/main/kotlin/C.kt"),
                ChangedFile("A", "src/main/kotlin/D.kt")
            ),
            "",
            conventional = true
        )

        val header = message.lineSequence().first()
        assertTrue(header.contains("add 4 files:"), "unexpected header: $header")
        assertTrue(header.contains("..."), "unexpected header: $header")
    }

    @Test
    fun `name status output is parsed`() {
        val parsed = GitStagedChanges.parseNameStatus(
            """
            A	src/New.kt
            M	src/Changed.kt
            D	src/Removed.kt

            """.trimIndent()
        )

        assertEquals(
            listOf(
                ChangedFile("A", "src/New.kt"),
                ChangedFile("M", "src/Changed.kt"),
                ChangedFile("D", "src/Removed.kt")
            ),
            parsed
        )
    }

    @Test
    fun `chat completions endpoint is normalized`() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            DeepSeekCommitMessageGenerator.endpoint("https://example.com/v1/")
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            DeepSeekCommitMessageGenerator.endpoint("https://example.com/v1/chat/completions")
        )
    }

    @Test
    fun `long subject is truncated`() {
        val result = HeuristicCommitMessageGenerator.truncate("x".repeat(100), 20)
        assertEquals(20, result.length)
        assertTrue(result.endsWith("..."))
    }
}
