package dev.ridergen.gitmsg

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Stores the DeepSeek token in the IDE credential store (PasswordSafe / OS keychain),
 * never in ridergen-gitmsg.xml.
 */
object DeepSeekTokenStore {

    private val attributes = CredentialAttributes(
        generateServiceName("RiderGenGitMsg", "DeepSeek API Token")
    )

    fun read(): String? =
        PasswordSafe.instance.getPassword(attributes)?.takeIf { it.isNotBlank() }

    fun write(token: String?) {
        PasswordSafe.instance.setPassword(attributes, token?.trim()?.takeIf { it.isNotBlank() })
    }

    /**
     * Uses PasswordSafe first, then falls back to the legacy plaintext value from 0.1.x
     * so that an upgrade keeps working until the user saves the settings once.
     */
    fun effectiveToken(settings: RiderGenSettings.State): String? =
        read() ?: settings.apiKey.takeIf { it.isNotBlank() }

    fun hasLegacyToken(settings: RiderGenSettings.State): Boolean =
        read() == null && settings.apiKey.isNotBlank()
}
