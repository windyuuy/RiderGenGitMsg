package dev.ridergen.gitmsg

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Reads the staged diff through the Git CLI. We intentionally avoid the Git plugin API:
 * it keeps this plugin independent of the bundled Git4Idea plugin and works the same way
 * as the command line the user is already using.
 */
object GitStagedChanges {

    private const val TIMEOUT_MS = 30_000

    fun read(@NotNull project: Project): StagedSnapshot {
        val workDirectory = project.basePath
            ?: throw IOException("无法确定项目根目录")

        val nameStatus = runGit(
            workDirectory,
            "diff", "--cached", "--name-status", "--no-renames"
        )
        val diff = runGit(
            workDirectory,
            "diff", "--cached", "--no-color", "--unified=3", "--no-ext-diff"
        )
        val stat = runGit(
            workDirectory,
            "diff", "--cached", "--stat", "--no-color"
        )

        return StagedSnapshot(parseNameStatus(nameStatus), diff, stat)
    }

    internal fun parseNameStatus(output: String): List<ChangedFile> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split('\t').filter(String::isNotBlank)
                if (parts.size < 2) null else ChangedFile(parts.first(), parts.last())
            }
            .toList()

    private fun runGit(workDirectory: String, vararg arguments: String): String {
        val commandLine = com.intellij.execution.configurations.GeneralCommandLine(
            listOf("git") + arguments
        )
            .withWorkDirectory(workDirectory)
            .withCharset(StandardCharsets.UTF_8)
            .withRedirectErrorStream(true)

        val result = CapturingProcessHandler(commandLine).runProcess(TIMEOUT_MS)
        if (result.isTimeout) {
            throw IOException("git 命令执行超时: git ${arguments.joinToString(" ")}")
        }
        if (result.exitCode != 0) {
            throw IOException(
                "git ${arguments.joinToString(" ")} 执行失败（退出码 ${result.exitCode}）：\n" +
                    result.stdout.take(800)
            )
        }
        return result.stdout
    }
}
