package dev.ridergen.gitmsg

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import java.nio.charset.StandardCharsets

/**
 * Windows Credential Manager backend (generic credential, machine-local persistence).
 *
 * Uses JNA that already ships with the IDE (`lib/util-8.jar`), so no extra bundled dependency
 * is required. Credential Manager stores the secret under the current Windows user; other
 * users cannot read it without administrative rights (DPAPI is user scoped, not app scoped).
 */
class WindowsCredentialTokenStore : TokenStore {

    override val id: String = ID

    override val displayName: String = "Windows 凭据管理器"

    override fun isAvailable(): Boolean = runCatching { api != null }.getOrDefault(false)

    override fun read(): String? {
        val library = api ?: return null
        val target = wideString(TARGET)
        val reference = PointerByReference()
        if (!library.CredReadW(target, CRED_TYPE_GENERIC, 0, reference)) {
            val error = Native.getLastError()
            if (error == ERROR_NOT_FOUND) return null
            throw IllegalStateException("CredReadW 失败（错误码 $error）")
        }
        val pointer = reference.value ?: return null
        try {
            val credential = CREDENTIALW(pointer)
            val size = credential.CredentialBlobSize
            val blob = credential.CredentialBlob
            if (size <= 0 || blob == null) return null
            val bytes = blob.getByteArray(0, size)
            return String(bytes, StandardCharsets.UTF_8).takeIf { it.isNotBlank() }
        } finally {
            library.CredFree(pointer)
        }
    }

    override fun write(token: String?) {
        val trimmed = token?.trim()?.takeIf { it.isNotBlank() }
        if (trimmed == null) {
            clear()
            return
        }
        val library = api ?: throw IllegalStateException("Windows 凭据管理器不可用")
        val bytes = trimmed.toByteArray(StandardCharsets.UTF_8)
        val blob = Memory(bytes.size.toLong())
        blob.write(0, bytes, 0, bytes.size)
        val target = wideString(TARGET)
        val userName = wideString(USER_NAME)
        val comment = wideString("AIGenCommitMsg DeepSeek API token")
        val credential = CREDENTIALW().apply {
            Type = CRED_TYPE_GENERIC
            TargetName = target
            CredentialBlobSize = bytes.size
            CredentialBlob = blob
            Persist = CRED_PERSIST_LOCAL_MACHINE
            UserName = userName
            Comment = comment
        }
        if (!library.CredWriteW(credential, 0)) {
            throw IllegalStateException("CredWriteW 失败（错误码 ${Native.getLastError()}）")
        }
    }

    override fun clear() {
        val library = api ?: return
        val target = wideString(TARGET)
        if (!library.CredDeleteW(target, CRED_TYPE_GENERIC, 0)) {
            val error = Native.getLastError()
            if (error != ERROR_NOT_FOUND) {
                throw IllegalStateException("CredDeleteW 失败（错误码 $error）")
            }
        }
    }

    /**
     * Builds a NUL-terminated UTF-16LE string. Passing [Memory] directly avoids relying on
     * `WString`, which is not available in every trimmed JNA distribution bundled with IDEs.
     */
    private fun wideString(value: String): Memory {
        val bytes = value.toByteArray(Charsets.UTF_16LE)
        val memory = Memory((bytes.size + 2).toLong())
        memory.write(0, bytes, 0, bytes.size)
        memory.setShort(bytes.size.toLong(), 0.toShort())
        return memory
    }

    private val api: Advapi32Ex?
        get() = ApiHolder.api

    /** JNA / Advapi32 is only loaded when this class is actually used on Windows. */
    private object ApiHolder {
        val api: Advapi32Ex? = runCatching {
            Native.load("advapi32", Advapi32Ex::class.java)
        }.getOrNull()
    }

    private interface Advapi32Ex : Library {
        fun CredWriteW(credential: CREDENTIALW, flags: Int): Boolean
        fun CredReadW(
            targetName: Pointer,
            type: Int,
            flags: Int,
            credential: PointerByReference
        ): Boolean
        fun CredDeleteW(targetName: Pointer, type: Int, flags: Int): Boolean
        fun CredFree(buffer: Pointer)
    }

    @Structure.FieldOrder(
        "Flags", "Type", "TargetName", "Comment", "LastWritten", "CredentialBlobSize",
        "CredentialBlob", "Persist", "AttributeCount", "Attributes", "TargetAlias", "UserName"
    )
    class CREDENTIALW : Structure {
        @JvmField
        var Flags: Int = 0

        @JvmField
        var Type: Int = 0

        @JvmField
        var TargetName: Pointer? = null

        @JvmField
        var Comment: Pointer? = null

        @JvmField
        var LastWritten: FILETIME = FILETIME()

        @JvmField
        var CredentialBlobSize: Int = 0

        @JvmField
        var CredentialBlob: Pointer? = null

        @JvmField
        var Persist: Int = 0

        @JvmField
        var AttributeCount: Int = 0

        @JvmField
        var Attributes: Pointer? = null

        @JvmField
        var TargetAlias: Pointer? = null

        @JvmField
        var UserName: Pointer? = null

        constructor() : super()

        constructor(pointer: Pointer) : super(pointer) {
            read()
        }
    }

    @Structure.FieldOrder("dwLowDateTime", "dwHighDateTime")
    class FILETIME : Structure() {
        @JvmField
        var dwLowDateTime: Int = 0

        @JvmField
        var dwHighDateTime: Int = 0
    }

    companion object {
        const val ID = "windows-credential"
        const val TARGET = "AIGenCommitMsg/DeepSeekToken"
        private const val USER_NAME = "AIGenCommitMsg"
        private const val CRED_TYPE_GENERIC = 1
        private const val CRED_PERSIST_LOCAL_MACHINE = 2
        private const val ERROR_NOT_FOUND = 1168
    }
}
