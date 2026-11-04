package dev.ridergen.gitmsg

/**
 * Abstraction for a place where the DeepSeek token can be persisted.
 *
 * The implementation chain is ordered by preference (system keychain first, then the IDE
 * PasswordSafe). New backends (environment variables, encrypted files, ...) only need to
 * implement this interface and be added to [TokenStorage]'s chain.
 *
 * Every method is expected to fail by throwing; callers ([TokenStorageService]) catch all
 * throwables and degrade to the next store.
 */
interface TokenStore {

    /** Stable identifier, e.g. `windows-credential`, `mac-keychain`, `password-safe`. */
    val id: String

    /** Human readable name shown in the settings page. */
    val displayName: String

    /** Cheap capability check; must never block for long and must not throw. */
    fun isAvailable(): Boolean

    /** Returns the stored token, or `null` when nothing is stored. */
    fun read(): String?

    /** Stores [token]; `null`/blank removes it. */
    fun write(token: String?)

    fun clear() = write(null)
}

/** In-memory store used by tests and as a last-resort backend. */
class InMemoryTokenStore(
    override val id: String,
    override val displayName: String,
    private var value: String? = null,
    var available: Boolean = true
) : TokenStore {

    override fun isAvailable(): Boolean = available

    override fun read(): String? = value

    override fun write(token: String?) {
        value = token?.trim()?.takeIf { it.isNotBlank() }
    }
}
