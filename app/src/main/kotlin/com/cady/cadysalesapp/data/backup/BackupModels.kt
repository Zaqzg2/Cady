package com.cady.cadysalesapp.data.backup

import com.cady.cadysalesapp.data.sync.RecordCounts
import java.io.File
import java.time.Instant

/** Where a backup came from. The kind is what retention is applied per. */
enum class BackupKind(val wire: String) {
    MANUAL("manual"),
    AUTO("auto"),

    /** Automatic safety snapshot taken right before any restore, so a restore can itself be undone. */
    PRE_RESTORE("pre_restore");

    companion object {
        fun fromWire(value: String?): BackupKind = entries.firstOrNull { it.wire == value } ?: MANUAL
    }
}

enum class AutoBackupFrequency(val days: Long) {
    DAILY(1),
    WEEKLY(7),
}

/** One saved backup file as shown in the list. [counts] is null when its manifest could not be read. */
data class BackupInfo(
    val file: File,
    val createdAt: Instant,
    val sizeBytes: Long,
    val kind: BackupKind,
    val counts: RecordCounts?,
    val ownerName: String?,
    val isValid: Boolean,
) {
    val name: String get() = file.name
}

/** What the confirm-restore dialog needs to show before anything is touched. */
data class BackupPreview(
    val createdAt: Instant,
    val kind: BackupKind,
    val counts: RecordCounts,
    val ownerUid: String?,
    val ownerName: String?,
    val appVersion: String?,
    val sizeBytes: Long,
)

data class RestoreResult(
    val restored: RecordCounts,
    /** Lines inside the backup that could not be read and were left out. */
    val unreadableRows: Int,
    val filesRestored: Int,
    val settingsRestored: Boolean,
)

/** A problem the user can act on; [message] is already Arabic, ready to show. */
class BackupException(message: String) : Exception(message)
