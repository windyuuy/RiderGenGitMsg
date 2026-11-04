package dev.ridergen.gitmsg

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.nio.charset.StandardCharsets

/**
 * macOS Keychain backend using the legacy `SecKeychain*` C API through JNA.
 *
 * Why the C API instead of the `security` CLI: no shell quoting, the secret never appears in
 * the process argument list/`ps`, and no parsing of human-oriented output is required. The
 * `SecKeychain*` functions are deprecated since macOS 10.15 but still supported, and they avoid
 * building a CoreFoundation dictionary through JNA (which would be far more error prone).
 */
class MacKeychainTokenStore : TokenStore {

    override val id: String = ID

    override val displayName: String = "macOS 钥匙串"

    override fun isAvailable(): Boolean = runCatching { library() != null }.getOrDefault(false)

    override fun read(): String? {
        val library = library() ?: return null
        val service = CString(SERVICE)
        val account = CString(ACCOUNT)
        val length = IntByReference()
        val data = PointerByReference()
        val item = PointerByReference()
        val status = library.SecKeychainFindGenericPassword(
            null,
            service.length, service.pointer,
            account.length, account.pointer,
            length, data, item
        )
        if (status == ERR_SEC_ITEM_NOT_FOUND) return null
        if (status != ERR_SEC_SUCCESS) {
            throw IllegalStateException("SecKeychainFindGenericPassword 失败（OSStatus $status）")
        }
        return try {
            val pointer = data.value
            val size = length.value
            if (pointer == null || size <= 0) return null
            String(pointer.getByteArray(0, size), StandardCharsets.UTF_8)
                .takeIf { it.isNotBlank() }
        } finally {
            library.SecKeychainItemFreeContent(null, data.value)
            release(item.value)
        }
    }

    override fun write(token: String?) {
        val trimmed = token?.trim()?.takeIf { it.isNotBlank() }
        val library = library() ?: throw IllegalStateException("macOS 钥匙串不可用")
        clear()
        if (trimmed == null) return

        val service = CString(SERVICE)
        val account = CString(ACCOUNT)
        val bytes = trimmed.toByteArray(StandardCharsets.UTF_8)
        val password = Memory(bytes.size.toLong())
        password.write(0, bytes, 0, bytes.size)
        val item = PointerByReference()
        val status = library.SecKeychainAddGenericPassword(
            null,
            service.length, service.pointer,
            account.length, account.pointer,
            bytes.size, password,
            item
        )
        release(item.value)
        if (status != ERR_SEC_SUCCESS) {
            throw IllegalStateException("SecKeychainAddGenericPassword 失败（OSStatus $status）")
        }
    }

    override fun clear() {
        val library = library() ?: return
        val service = CString(SERVICE)
        val account = CString(ACCOUNT)
        val length = IntByReference()
        val data = PointerByReference()
        val item = PointerByReference()
        val status = library.SecKeychainFindGenericPassword(
            null,
            service.length, service.pointer,
            account.length, account.pointer,
            length, data, item
        )
        if (status == ERR_SEC_ITEM_NOT_FOUND) return
        if (status != ERR_SEC_SUCCESS) {
            throw IllegalStateException("SecKeychainFindGenericPassword 失败（OSStatus $status）")
        }
        try {
            library.SecKeychainItemFreeContent(null, data.value)
            val itemPointer = item.value ?: return
            val deleteStatus = library.SecKeychainItemDelete(itemPointer)
            if (deleteStatus != ERR_SEC_SUCCESS && deleteStatus != ERR_SEC_ITEM_NOT_FOUND) {
                throw IllegalStateException("SecKeychainItemDelete 失败（OSStatus $deleteStatus）")
            }
        } finally {
            release(item.value)
        }
    }

    private fun release(pointer: Pointer?) {
        if (pointer == null) return
        runCatching { coreFoundation()?.CFRelease(pointer) }
    }

    private fun library(): SecurityEx? = SecurityHolder.library

    private fun coreFoundation(): CoreFoundationEx? = CoreFoundationHolder.library

    /** UTF-8 C string that keeps its backing memory alive for the duration of the call. */
    private class CString(value: String) {
        val length: Int = value.toByteArray(StandardCharsets.UTF_8).size
        val memory: Memory = Memory((length + 1).toLong()).apply {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            write(0, bytes, 0, bytes.size)
            setByte(bytes.size.toLong(), 0)
        }

        val pointer: Pointer
            get() = memory
    }

    private interface SecurityEx : Library {
        fun SecKeychainAddGenericPassword(
            keychain: Pointer?,
            serviceNameLength: Int,
            serviceName: Pointer,
            accountNameLength: Int,
            accountName: Pointer,
            passwordLength: Int,
            passwordData: Pointer,
            itemRef: PointerByReference?
        ): Int

        fun SecKeychainFindGenericPassword(
            keychain: Pointer?,
            serviceNameLength: Int,
            serviceName: Pointer,
            accountNameLength: Int,
            accountName: Pointer,
            passwordLength: IntByReference,
            passwordData: PointerByReference,
            itemRef: PointerByReference?
        ): Int

        fun SecKeychainItemFreeContent(attributeList: Pointer?, data: Pointer?)

        fun SecKeychainItemDelete(itemRef: Pointer): Int
    }

    private interface CoreFoundationEx : Library {
        fun CFRelease(value: Pointer)
    }

    private object SecurityHolder {
        val library: SecurityEx? = runCatching {
            Native.load("Security", SecurityEx::class.java)
        }.getOrNull()
    }

    private object CoreFoundationHolder {
        val library: CoreFoundationEx? = runCatching {
            Native.load("CoreFoundation", CoreFoundationEx::class.java)
        }.getOrNull()
    }

    companion object {
        const val ID = "mac-keychain"
        private const val SERVICE = "AIGenCommitMsg"
        private const val ACCOUNT = "AIGenCommitMsg/DeepSeekToken"
        private const val ERR_SEC_SUCCESS = 0
        private const val ERR_SEC_ITEM_NOT_FOUND = -25300
    }
}
