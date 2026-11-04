package dev.ridergen.gitmsg

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Linux Secret Service backend.
 *
 * Uses the `secret-tool` CLI (the reference libsecret front-end) instead of calling D-Bus /
 * libsecret through JNA. Reasons:
 * - no bundled native library or JNA bindings to maintain;
 * - the secret is passed through stdin, never through the process argument list (`ps` safe);
 * - lookups exit with code 1 when no item matches, which is easy to distinguish from failures.
 *
 * The store reports itself unavailable on headless/CI machines (no D-Bus session and no
 * display) so startup and unit tests never wait on a desktop service.
 */
class LinuxSecretServiceTokenStore : TokenStore {

    override val id: String = ID

    override val displayName: String = "Linux Secret Service"

    override fun isAvailable(): Boolean = isLinux() && commandExists() && hasDesktopSession()

    override fun read(): String? {
        val result = run(
            listOf("lookup", "service", SERVICE, "account", ACCOUNT),
            stdin = null
        )
        if (result.exitCode == 1 && result.output.isBlank()) return null
        if (result.exitCode != 0) {
            throw IllegalStateException(
                "secret-tool lookup 失败（退出码 ${result.exitCode}）：${result.output.take(200)}"
            )
        }
        return result.output.trimEnd('\n', '\r').takeIf { it.isNotBlank() }
    }

    override fun write(token: String?) {
        val trimmed = token?.trim()?.takeIf { it.isNotBlank() }
        if (trimmed == null) {
            clear()
            return
        }
        val result = run(
            listOf("store", "--label=$LABEL", "service", SERVICE, "account", ACCOUNT),
            stdin = trimmed
        )
        if (result.exitCode != 0) {
            throw IllegalStateException(
                "secret-tool store 失败（退出码 ${result.exitCode}）：${result.output.take(200)}"
            )
        }
    }

    override fun clear() {
        val result = run(listOf("clear", "service", SERVICE, "account", ACCOUNT), stdin = null)
        // `clear` exits with 1 when there is nothing to delete.
        if (result.exitCode != 0 && result.exitCode != 1) {
            throw IllegalStateException(
                "secret-tool clear 失败（退出码 ${result.exitCode}）：${result.output.take(200)}"
            )
        }
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    private fun run(arguments: List<String>, stdin: String?): ProcessResult {
        val process = ProcessBuilder(listOf("secret-tool") + arguments).start()
        try {
            if (stdin != null) {
                process.outputStream.use { it.write(stdin.toByteArray(StandardCharsets.UTF_8)) }
            } else {
                process.outputStream.close()
            }
            // Read before waiting so a filled pipe can never deadlock.
            val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IOException("secret-tool 执行超时（${TIMEOUT_SECONDS} 秒）")
            }
            return ProcessResult(process.exitValue(), output)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun isLinux(): Boolean =
        System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)

    private fun commandExists(): Boolean {
        val path = System.getenv("PATH") ?: return false
        return path.split(File.pathSeparatorChar).any { directory ->
            directory.isNotBlank() && File(directory, "secret-tool").let { it.isFile && it.canExecute() }
        }
    }

    private fun hasDesktopSession(): Boolean =
        !System.getenv("DBUS_SESSION_BUS_ADDRESS").isNullOrBlank() ||
            !System.getenv("DISPLAY").isNullOrBlank() ||
            !System.getenv("WAYLAND_DISPLAY").isNullOrBlank()

    companion object {
        const val ID = "linux-secret-service"
        private const val SERVICE = "AIGenCommitMsg"
        private const val ACCOUNT = "AIGenCommitMsg/DeepSeekToken"
        private const val LABEL = "AIGenCommitMsg DeepSeek Token"
        private const val TIMEOUT_SECONDS = 8L
    }
}
