package dev.ridergen.gitmsg

/**
 * Ordered credential store chain with migration and failure protection.
 *
 * Order is supplied by the caller: normally `[platform keychain, IDE PasswordSafe]`.
 * The first available store is the primary target; lower-priority stores are only read when
 * the primary has no token, and their token is then migrated upward.
 *
 * Migration always follows: write new → read back and compare → delete old → clear legacy XML.
 * If any step fails the old value is kept, so a token is never lost.
 */
class TokenStorageService(
    private val stores: List<TokenStore>,
    private val legacyTokenProvider: () -> String? = { null },
    private val clearLegacyToken: () -> Unit = {}
) {

    data class MigrationResult(
        val success: Boolean,
        val from: String,
        val to: String,
        val message: String
    )

    data class ReadResult(
        val token: CharArray?,
        val storeId: String?,
        val storeName: String?,
        val migration: MigrationResult?,
        val errors: List<String>
    ) : AutoCloseable {

        fun hasToken(): Boolean = token != null && token.isNotEmpty()

        override fun close() {
            token?.fill('\u0000')
        }
    }

    data class WriteResult(
        val success: Boolean,
        val storeName: String?,
        val message: String
    )

    data class StoreStatus(
        val id: String,
        val name: String,
        val available: Boolean,
        val error: String?
    )

    fun storeStatus(): List<StoreStatus> = stores.map { store ->
        try {
            StoreStatus(store.id, store.displayName, store.isAvailable(), null)
        } catch (t: Throwable) {
            StoreStatus(store.id, store.displayName, false, t.message ?: t.javaClass.simpleName)
        }
    }

    /** Returns the first available store, or null when the whole chain is unusable. */
    fun primaryStore(): TokenStore? = availableStores().firstOrNull()?.first

    fun readToken(): ReadResult {
        val errors = mutableListOf<String>()
        val available = availableStores(errors)
        if (available.isEmpty()) {
            return ReadResult(null, null, null, null, errors + "没有可用的凭据存储")
        }

        val primary = available.first().first
        val primaryToken = readQuietly(primary, errors)
        if (!primaryToken.isNullOrBlank()) {
            return ReadResult(
                primaryToken.toCharArray(),
                primary.id,
                primary.displayName,
                null,
                errors
            )
        }

        for (lower in available.drop(1)) {
            val store = lower.first
            val lowerToken = readQuietly(store, errors)
            if (lowerToken.isNullOrBlank()) continue
            val migration = migrateToken(from = store, to = primary, token = lowerToken)
            val active = if (migration.success) primary else store
            return ReadResult(
                lowerToken.toCharArray(),
                active.id,
                active.displayName,
                migration,
                errors
            )
        }

        val legacy = runCatching { legacyTokenProvider() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (legacy != null) {
            val migration = migrateLegacyTo(primary, legacy)
            return ReadResult(
                legacy.toCharArray(),
                primary.id,
                primary.displayName,
                migration,
                errors
            )
        }

        return ReadResult(null, primary.id, primary.displayName, null, errors)
    }

    /**
     * Writes the token to the first store that accepts and verifies it. If the primary system
     * keychain fails, the PasswordSafe fallback keeps working and the result reports the
     * downgrade instead of throwing.
     */
    fun writeToken(token: String): WriteResult {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            val cleared = clearAll()
            return WriteResult(cleared.success, cleared.storeName, cleared.message)
        }

        val errors = mutableListOf<String>()
        for (store in availableStores(errors).map { it.first }) {
            try {
                store.write(trimmed)
                val readBack = store.read()
                if (readBack == trimmed) {
                    // The new value is verified; drop a stale PasswordSafe copy if the system
                    // keychain is now the primary store.
                    if (!isPasswordSafe(store)) {
                        stores.filter(::isPasswordSafe).forEach { passwordSafe ->
                            runCatching { passwordSafe.clear() }
                        }
                    }
                    return WriteResult(true, store.displayName, "Token 已保存到 ${store.displayName}")
                }
                errors += "${store.displayName} 回读校验失败"
            } catch (t: Throwable) {
                errors += "${store.displayName} 写入失败：${t.message ?: t.javaClass.simpleName}"
            }
        }
        return WriteResult(false, null, "Token 写入失败：${errors.joinToString("；")}")
    }

    fun clearAll(): WriteResult {
        val errors = mutableListOf<String>()
        for (store in availableStores(errors).map { it.first }) {
            try {
                store.clear()
            } catch (t: Throwable) {
                errors += "${store.displayName} 清除失败：${t.message ?: t.javaClass.simpleName}"
            }
        }
        try {
            clearLegacyToken()
        } catch (t: Throwable) {
            errors += "清除旧版明文配置失败：${t.message ?: t.javaClass.simpleName}"
        }
        return if (errors.isEmpty()) {
            WriteResult(true, null, "Token 已从所有存储中清除")
        } else {
            WriteResult(false, null, "清除未完全成功：${errors.joinToString("；")}")
        }
    }

    /**
     * Explicit "migrate to system keychain" action for the settings page.
     * Returns null when the platform has no system keychain at all.
     */
    fun migrateToSystem(): MigrationResult? {
        val system = stores.firstOrNull { !isPasswordSafe(it) && safeAvailable(it) }
            ?: return MigrationResult(
                false,
                "",
                "",
                "当前平台没有可用的系统钥匙串，继续使用 IDE PasswordSafe"
            )
        val passwordSafe = stores.firstOrNull { isPasswordSafe(it) && safeAvailable(it) }

        val passwordSafeToken = passwordSafe?.let { readQuietly(it, mutableListOf()) }
        if (!passwordSafeToken.isNullOrBlank()) {
            val systemToken = readQuietly(system, mutableListOf())
            if (!systemToken.isNullOrBlank() && systemToken != passwordSafeToken) {
                return MigrationResult(
                    false,
                    passwordSafe.displayName,
                    system.displayName,
                    "系统钥匙串中已有不同的 Token，未覆盖；请先清除 Token 后重试"
                )
            }
            return migrateToken(passwordSafe, system, passwordSafeToken)
        }

        val legacy = runCatching { legacyTokenProvider() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (legacy != null) return migrateLegacyTo(system, legacy)

        val current = readQuietly(system, mutableListOf())
        return if (!current.isNullOrBlank()) {
            MigrationResult(true, "", system.displayName, "Token 已在 ${system.displayName} 中，无需迁移")
        } else {
            MigrationResult(false, "", system.displayName, "没有需要迁移的 Token")
        }
    }

    private fun migrateToken(from: TokenStore, to: TokenStore, token: String): MigrationResult = try {
        to.write(token)
        if (to.read() != token) {
            MigrationResult(
                false,
                from.displayName,
                to.displayName,
                "迁移未完成：回读校验失败，保留了 ${from.displayName} 中的 Token"
            )
        } else {
            from.clear()
            MigrationResult(true, from.displayName, to.displayName, "已迁移到 ${to.displayName}")
        }
    } catch (t: Throwable) {
        MigrationResult(
            false,
            from.displayName,
            to.displayName,
            "迁移未完成：${t.message ?: t.javaClass.simpleName}；仍使用 ${from.displayName}"
        )
    }

    private fun migrateLegacyTo(primary: TokenStore, token: String): MigrationResult = try {
        primary.write(token)
        if (primary.read() != token) {
            MigrationResult(
                false,
                "旧版明文配置",
                primary.displayName,
                "迁移未完成：回读校验失败，已保留 ridergen-gitmsg.xml 中的旧值"
            )
        } else {
            clearLegacyToken()
            MigrationResult(
                true,
                "旧版明文配置",
                primary.displayName,
                "已自动迁移旧版明文 Token 到 ${primary.displayName} 并清除 XML 中的明文"
            )
        }
    } catch (t: Throwable) {
        MigrationResult(
            false,
            "旧版明文配置",
            primary.displayName,
            "迁移未完成：${t.message ?: t.javaClass.simpleName}；旧值仍保留在配置文件中"
        )
    }

    private fun availableStores(errors: MutableList<String>? = null): List<Pair<TokenStore, Boolean>> =
        stores.map { store ->
            val available = try {
                store.isAvailable()
            } catch (t: Throwable) {
                errors?.add("${store.displayName} 不可用：${t.message ?: t.javaClass.simpleName}")
                false
            }
            store to available
        }.filter { it.second }

    private fun readQuietly(store: TokenStore, errors: MutableList<String>): String? = try {
        store.read()
    } catch (t: Throwable) {
        errors.add("${store.displayName} 读取失败：${t.message ?: t.javaClass.simpleName}")
        null
    }

    private fun safeAvailable(store: TokenStore): Boolean =
        runCatching { store.isAvailable() }.getOrDefault(false)

    private fun isPasswordSafe(store: TokenStore): Boolean = store.id == PasswordSafeTokenStore.ID
}

/**
 * Default chain for the running IDE: platform keychain first, PasswordSafe as fallback.
 * New backends (environment variables, encrypted files, ...) can be inserted here later.
 */
object TokenStorage {

    private val service: TokenStorageService by lazy {
        val settings = RiderGenSettings.getInstance()
        TokenStorageService(
            stores = listOfNotNull(platformTokenStore(), PasswordSafeTokenStore()),
            legacyTokenProvider = { settings.current.apiKey.takeIf { it.isNotBlank() } },
            clearLegacyToken = { settings.update { state -> state.apiKey = "" } }
        )
    }

    fun service(): TokenStorageService = service

    /** Platform keychain for the current OS; null on unsupported platforms. */
    internal fun platformTokenStore(osName: String = System.getProperty("os.name").orEmpty()): TokenStore? =
        when {
            osName.contains("win", ignoreCase = true) -> WindowsCredentialTokenStore()
            osName.contains("mac", ignoreCase = true) -> MacKeychainTokenStore()
            osName.contains("linux", ignoreCase = true) -> LinuxSecretServiceTokenStore()
            else -> null
        }
}
