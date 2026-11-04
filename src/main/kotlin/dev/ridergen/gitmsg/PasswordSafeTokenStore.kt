package dev.ridergen.gitmsg

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * IDE credential store (PasswordSafe). On macOS this is backed by the system Keychain; on
 * Windows/Linux it is the IDE KeePass database. The service name and key are kept unchanged
 * from 0.2.0 so that existing tokens keep working.
 */
class PasswordSafeTokenStore : TokenStore {

    override val id: String = ID

    override val displayName: String = "IDE PasswordSafe"

    private val attributes: CredentialAttributes by lazy {
        CredentialAttributes(generateServiceName(SERVICE_NAME, KEY))
    }

    override fun isAvailable(): Boolean = runCatching { PasswordSafe.instance }.isSuccess

    override fun read(): String? =
        PasswordSafe.instance.getPassword(attributes)?.takeIf { it.isNotBlank() }

    override fun write(token: String?) {
        PasswordSafe.instance.setPassword(attributes, token?.trim()?.takeIf { it.isNotBlank() })
    }

    companion object {
        const val ID = "password-safe"
        const val SERVICE_NAME = "RiderGenGitMsg"
        const val KEY = "DeepSeek API Token"
    }
}
