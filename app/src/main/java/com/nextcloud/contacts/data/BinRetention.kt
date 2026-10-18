package com.nextcloud.contacts.data

import com.nextcloud.contacts.data.local.ContactEntity

/**
 * How long a contact stays in the bin after the user deletes it.
 * The server card is removed on the next sync. This window is only the local copy.
 */
const val BIN_RETENTION_MS: Long = 3L * 24L * 60L * 60L * 1000L

private const val DAY_MS: Long = 24L * 60L * 60L * 1000L

fun binRetentionExpired(deletedAtMillis: Long, nowMillis: Long): Boolean =
    nowMillis - deletedAtMillis >= BIN_RETENTION_MS

/** Whole days still in the bin, rounded up. Zero once the hold has ended. */
fun binDaysRemaining(deletedAt: Long, now: Long): Int {
    val remaining = deletedAt + BIN_RETENTION_MS - now
    if (remaining <= 0L) return 0
    return ((remaining + DAY_MS - 1L) / DAY_MS).toInt().coerceAtLeast(1)
}

/**
 * The server card is gone. Keep the soft-deleted row, without a href, until the bin window ends.
 * [ContactEntity.lastModified] stays the time the user deleted it.
 */
fun contactAfterServerDeleteKept(contact: ContactEntity): ContactEntity =
    contact.copy(
        remoteUrl = null,
        etag = null,
        dirty = false,
        deleted = true,
    )

/**
 * The next sync uploads DELETE while the server href is still stored.
 * The bin clock does not delay that upload.
 */
fun serverDeleteIsDue(remoteUrl: String?, deleted: Boolean): Boolean =
    deleted && !remoteUrl.isNullOrBlank()

/** Age-based cleanup applies only after the server delete has cleared the href. */
fun shouldPurgeBinContact(remoteUrl: String?, deletedAtMillis: Long, nowMillis: Long): Boolean =
    remoteUrl == null && binRetentionExpired(deletedAtMillis, nowMillis)

fun shouldKeepDeletedContactAfterServerGone(contact: ContactEntity, nowMillis: Long): Boolean =
    contact.deleted && !binRetentionExpired(contact.lastModified, nowMillis)
