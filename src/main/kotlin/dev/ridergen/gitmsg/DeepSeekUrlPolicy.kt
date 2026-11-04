package dev.ridergen.gitmsg

import java.net.URI

/**
 * Base URL policy. Validated before every request, not only when settings are saved.
 *
 * Rules:
 * - default official host: https://api.deepseek.com (port 443 or unspecified)
 * - https only
 * - userinfo (`user:pass@host`) is rejected
 * - ports other than 443 are rejected
 * - custom hosts require an explicit confirmation checkbox
 * - `http://localhost` is only allowed when the localhost debug switch is enabled
 */
object DeepSeekUrlPolicy {

    const val OFFICIAL_HOST = "api.deepseek.com"

    sealed interface Result {
        data object Allowed : Result
        data class Rejected(val reason: String) : Result
    }

    fun validate(
        raw: String,
        customBaseUrlConfirmed: Boolean,
        allowLocalhostHttp: Boolean
    ): Result {
        val value = raw.trim()
        if (value.isEmpty()) return Result.Rejected("Base URL 不能为空，默认应为 $OFFICIAL_HOST")

        val uri = runCatching { URI(value) }.getOrNull()
            ?: return Result.Rejected("URL 格式不合法")
        if (uri.userInfo != null) {
            return Result.Rejected("URL 不能包含用户名或密码")
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
            ?: return Result.Rejected("URL 缺少主机名")

        val loopback = isLoopback(host)
        if (loopback && allowLocalhostHttp) {
            // Local debugging: allow any port and http/https, no confirmation needed.
            return Result.Allowed
        }

        if (scheme != "https") {
            return Result.Rejected(
                if (loopback) {
                    "仅允许 https；本机 http 调试需显式开启 localhost HTTP 开关"
                } else {
                    "仅允许 https 地址"
                }
            )
        }
        if (uri.port != -1 && uri.port != 443) {
            return Result.Rejected("只允许 443 端口（当前为 ${uri.port}）")
        }
        if (host != OFFICIAL_HOST && !customBaseUrlConfirmed) {
            return Result.Rejected(
                "自定义 Base URL 需要勾选确认“Token 会发送到该地址”"
            )
        }
        return Result.Allowed
    }

    fun isCustomHost(raw: String): Boolean {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host != OFFICIAL_HOST
    }

    fun hostOf(raw: String): String? = runCatching { URI(raw.trim()).host?.lowercase() }.getOrNull()

    fun isLoopback(host: String): Boolean =
        host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host == "[::1]" ||
            host.endsWith(".localhost")
}
