package dev.ridergen.gitmsg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStorageServiceTest {

    private class FakeStore(
        override val id: String,
        override val displayName: String,
        var token: String? = null,
        var available: Boolean = true,
        var failRead: Boolean = false,
        var failWrite: Boolean = false,
        var failClear: Boolean = false,
        var corruptReadBack: Boolean = false
    ) : TokenStore {

        private var nextRead: String? = null

        override fun isAvailable(): Boolean = available

        override fun read(): String? {
            if (failRead) throw IllegalStateException("read failed")
            nextRead?.let {
                nextRead = null
                return it
            }
            return token
        }

        override fun write(token: String?) {
            if (failWrite) throw IllegalStateException("write failed")
            this.token = token
            if (corruptReadBack) nextRead = "corrupted"
        }

        override fun clear() {
            if (failClear) throw IllegalStateException("clear failed")
            token = null
        }
    }

    private fun chain(
        system: FakeStore,
        passwordSafe: FakeStore
    ) = listOf(system, passwordSafe)

    @Test
    fun `system store success is used directly`() {
        val system = FakeStore("system", "系统钥匙串", token = "token-1")
        val passwordSafe = FakeStore(PasswordSafeTokenStore.ID, "PS")
        val result = TokenStorageService(chain(system, passwordSafe)).readToken()

        assertEquals("token-1", result.token?.concatToString())
        assertEquals("system", result.storeId)
        assertNull(result.migration)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `unavailable system store falls back to password safe`() {
        val system = FakeStore("system", "系统钥匙串", available = false)
        val passwordSafe = FakeStore(PasswordSafeTokenStore.ID, "PS", token = "token-2")
        val result = TokenStorageService(chain(system, passwordSafe)).readToken()

        assertEquals("token-2", result.token?.concatToString())
        assertEquals(PasswordSafeTokenStore.ID, result.storeId)
    }

    @Test
    fun `both stores unavailable returns no token without throwing`() {
        val system = FakeStore("system", "系统钥匙串", available = false)
        val passwordSafe = FakeStore(PasswordSafeTokenStore.ID, "PS", available = false)
        val result = TokenStorageService(chain(system, passwordSafe)).readToken()

        assertFalse(result.hasToken())
        assertTrue(result.errors.any { it.contains("没有可用的凭据存储") }, result.errors.toString())
    }

    @Test
    fun `password safe token is migrated to the system store`() {
        val system = FakeStore("system", "系统钥匙串")
        val passwordSafe = FakeStore(PasswordSafeTokenStore.ID, "PS", token = "token-3")
        val result = TokenStorageService(chain(system, passwordSafe)).readToken()

        assertEquals("token-3", result.token?.concatToString())
        assertEquals("system", result.storeId)
        assertNotNull(result.migration)
        assertTrue(result.migration.success)
        assertEquals("token-3", system.token)
        assertNull(passwordSafe.token)
    }

    @Test
    fun `failed migration keeps the password safe token`() {
        val system = FakeStore("system", "系统钥匙串", failWrite = true)
        val passwordSafe = FakeStore(PasswordSafeTokenStore.ID, "PS", token = "token-4")
        val result = TokenStorageService(chain(system, passwordSafe)).readToken()

        assertEquals("token-4", result.token?.concatToString())
        assertEquals(PasswordSafeTokenStore.ID, result.storeId)
        assertNotNull(result.migration)
        assertFalse(result.migration.success)
        assertEquals("token-4", passwordSafe.token)
    }

    @Test
    fun `failed read back comparison keeps the password safe token`() {
        val system = FakeStore("system", "系统钥匙串", corruptReadBack = true)
        val passwordSafe = FakeStore(PasswordSafeTokenStore.ID, "PS", token = "token-5")
        val result = TokenStorageService(chain(system, passwordSafe)).readToken()

        assertEquals("token-5", result.token?.concatToString())
        assertEquals(PasswordSafeTokenStore.ID, result.storeId)
        assertNotNull(result.migration)
        assertFalse(result.migration.success)
        assertEquals("token-5", passwordSafe.token)
    }

    @Test
    fun `legacy plaintext is migrated and cleared`() {
        var legacy: String? = "legacy-token"
        val system = FakeStore("system", "系统钥匙串")
        val service = TokenStorageService(
            chain(system, FakeStore(PasswordSafeTokenStore.ID, "PS")),
            legacyTokenProvider = { legacy },
            clearLegacyToken = { legacy = null }
        )

        val result = service.readToken()
        assertEquals("legacy-token", result.token?.concatToString())
        assertNotNull(result.migration)
        assertTrue(result.migration.success)
        assertEquals("legacy-token", system.token)
        assertNull(legacy)
    }

    @Test
    fun `legacy plaintext is kept when migration fails`() {
        var legacy: String? = "legacy-token"
        val system = FakeStore("system", "系统钥匙串", failWrite = true)
        val service = TokenStorageService(
            chain(system, FakeStore(PasswordSafeTokenStore.ID, "PS")),
            legacyTokenProvider = { legacy },
            clearLegacyToken = { legacy = null }
        )

        val result = service.readToken()
        assertEquals("legacy-token", result.token?.concatToString())
        assertNotNull(result.migration)
        assertFalse(result.migration.success)
        assertEquals("legacy-token", legacy)
    }

    @Test
    fun `write falls back to password safe when the system store fails`() {
        val system = FakeStore("system", "系统钥匙串", failWrite = true)
        val passwordSafe = FakeStore(PasswordSafeTokenStore.ID, "PS")
        val result = TokenStorageService(chain(system, passwordSafe)).writeToken("token-6")

        assertTrue(result.success, result.message)
        assertEquals("PS", result.storeName)
        assertNull(system.token)
        assertEquals("token-6", passwordSafe.token)
    }

    @Test
    fun `write to the system store clears a stale password safe copy`() {
        val system = FakeStore("system", "系统钥匙串")
        val passwordSafe = FakeStore(PasswordSafeTokenStore.ID, "PS", token = "old-token")
        val result = TokenStorageService(chain(system, passwordSafe)).writeToken("token-7")

        assertTrue(result.success, result.message)
        assertEquals("token-7", system.token)
        assertNull(passwordSafe.token)
    }

    @Test
    fun `clear removes every store and the legacy value`() {
        var legacy: String? = "legacy"
        val system = FakeStore("system", "系统钥匙串", token = "a")
        val passwordSafe = FakeStore(PasswordSafeTokenStore.ID, "PS", token = "b")
        val service = TokenStorageService(
            chain(system, passwordSafe),
            legacyTokenProvider = { legacy },
            clearLegacyToken = { legacy = null }
        )

        val result = service.clearAll()
        assertTrue(result.success, result.message)
        assertNull(system.token)
        assertNull(passwordSafe.token)
        assertNull(legacy)
    }

    @Test
    fun `explicit migration moves password safe to the system store`() {
        val system = FakeStore("system", "系统钥匙串")
        val passwordSafe = FakeStore(PasswordSafeTokenStore.ID, "PS", token = "token-8")
        val migration = TokenStorageService(chain(system, passwordSafe)).migrateToSystem()

        assertNotNull(migration)
        assertTrue(migration.success)
        assertEquals("token-8", system.token)
        assertNull(passwordSafe.token)
    }

    @Test
    fun `explicit migration refuses to overwrite a different system token`() {
        val system = FakeStore("system", "系统钥匙串", token = "system-token")
        val passwordSafe = FakeStore(PasswordSafeTokenStore.ID, "PS", token = "other-token")
        val migration = TokenStorageService(chain(system, passwordSafe)).migrateToSystem()

        assertNotNull(migration)
        assertFalse(migration.success)
        assertEquals("system-token", system.token)
        assertEquals("other-token", passwordSafe.token)
    }
}
