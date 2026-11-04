package dev.ridergen.gitmsg

data class ChangedFile(val status: String, val path: String) {

    val kind: ChangeKind
        get() = when (status.firstOrNull()?.uppercaseChar()) {
            'A' -> ChangeKind.ADDED
            'M' -> ChangeKind.MODIFIED
            'D' -> ChangeKind.DELETED
            'R', 'C' -> ChangeKind.RENAMED
            else -> ChangeKind.OTHER
        }

    enum class ChangeKind {
        ADDED, MODIFIED, DELETED, RENAMED, OTHER
    }
}

data class StagedSnapshot(
    val files: List<ChangedFile>,
    val diff: String,
    val stat: String
)
