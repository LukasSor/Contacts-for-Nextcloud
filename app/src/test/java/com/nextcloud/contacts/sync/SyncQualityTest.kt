package com.nextcloud.contacts.sync

import com.nextcloud.contacts.data.FingerprintIndex
import com.nextcloud.contacts.data.contactAfterFavoriteToggle
import com.nextcloud.contacts.data.contactAfterServerCardMissing
import com.nextcloud.contacts.data.contactWithFavorite
import com.nextcloud.contacts.data.remoteUidForPush
import com.nextcloud.contacts.data.RemoteContactIndex
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.FingerprintContactRow
import com.nextcloud.contacts.data.remote.davStatusCode
import com.nextcloud.contacts.data.remote.failedPropstatCode
import com.nextcloud.contacts.data.remote.syncReportCardIsGone
import com.nextcloud.contacts.ui.viewmodel.detailFieldSaveNotifiesOnUploadFailure
import com.nextcloud.contacts.ui.viewmodel.shouldRevertFavorite
import com.nextcloud.contacts.util.parseIsoOrBasicLocalDate
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncQualityTest {

    @Test
    fun secondSyncJoinsTheRunInFlight() = runTest {
        val gate = SyncSingleFlight()
        var runs = 0
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            gate.coalesce(fullPull = false) {
                runs++
                started.complete(Unit)
                release.await()
                SyncManager.SyncResult.Success(pulled = 1)
            }
        }
        started.await()
        val second = async {
            gate.coalesce(fullPull = false) {
                runs++
                SyncManager.SyncResult.Success(pulled = 2)
            }
        }
        yield()
        release.complete(Unit)
        val firstResult = first.await() as SyncManager.SyncResult.Success
        val secondResult = second.await() as SyncManager.SyncResult.Success
        assertEquals(1, runs)
        assertEquals(1, firstResult.pulled)
        assertEquals(1, secondResult.pulled)
    }

    @Test
    fun fullPullWaitsInsteadOfJoiningIncremental() = runTest {
        val gate = SyncSingleFlight()
        var runs = 0
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val incremental = async {
            gate.coalesce(fullPull = false) {
                runs++
                started.complete(Unit)
                release.await()
                SyncManager.SyncResult.Success(pulled = 1)
            }
        }
        started.await()
        val full = async {
            gate.coalesce(fullPull = true) {
                runs++
                SyncManager.SyncResult.Success(pulled = 9)
            }
        }
        yield()
        assertEquals(1, runs)
        release.complete(Unit)
        incremental.await()
        val fullResult = full.await() as SyncManager.SyncResult.Success
        assertEquals(2, runs)
        assertEquals(9, fullResult.pulled)
    }

    @Test
    fun nestedFavoriteDoesNotDeadlock() = runTest {
        val gate = SyncSingleFlight()
        val result = gate.coalesce(fullPull = false) {
            gate.exclude { SyncManager.SyncResult.Success(pushed = 3) }
        } as SyncManager.SyncResult.Success
        assertEquals(3, result.pushed)
    }

    @Test
    fun authFailureDoesNotRetryButServerErrorsDo() {
        assertEquals(
            SyncRetryPolicy.WorkAction.FAIL,
            SyncRetryPolicy.workAction(SyncManager.SyncResult.Error("Sync failed: HTTP 401")),
        )
        assertEquals(
            SyncRetryPolicy.WorkAction.FAIL,
            SyncRetryPolicy.workAction(SyncManager.SyncResult.Error("Favorite failed: HTTP 403 Unauthorized")),
        )
        assertEquals(
            SyncRetryPolicy.WorkAction.RETRY,
            SyncRetryPolicy.workAction(SyncManager.SyncResult.Error("Sync failed: HTTP 503")),
        )
        assertEquals(
            SyncRetryPolicy.WorkAction.RETRY,
            SyncRetryPolicy.workAction(SyncManager.SyncResult.Error("Failed to connect to cloud.example")),
        )
        assertEquals(
            SyncRetryPolicy.WorkAction.SUCCEED,
            SyncRetryPolicy.workAction(SyncManager.SyncResult.Success()),
        )
        assertEquals(
            SyncRetryPolicy.WorkAction.SUCCEED,
            SyncRetryPolicy.workAction(SyncManager.SyncResult.NotLoggedIn),
        )
        assertFalse(SyncRetryPolicy.isTransientFailure("Favorite failed: HTTP 401"))
        assertTrue(SyncRetryPolicy.isTransientFailure("Favorite failed: HTTP 503"))
        assertFalse(SyncRetryPolicy.isTransientFailure("Favorite failed: HTTP 400"))
    }

    @Test
    fun favorite400DoesNotLeaveACleanRejectedStar() {
        assertTrue(SyncRetryPolicy.shouldMarkDirtyAfterFavoriteFailure("Favorite failed: HTTP 400"))
        assertTrue(SyncRetryPolicy.shouldMarkDirtyAfterFavoriteFailure("Favorite failed: HTTP 503"))
        assertFalse(SyncRetryPolicy.shouldMarkDirtyAfterFavoriteFailure("Favorite failed: HTTP 401"))
        assertFalse(SyncRetryPolicy.shouldMarkDirtyAfterFavoriteFailure("Favorite failed: HTTP 403"))
        assertFalse(SyncRetryPolicy.shouldMarkDirtyAfterFavoriteFailure(null))
        var dirtyAfterMarkSynced = false
        if (SyncRetryPolicy.shouldMarkDirtyAfterFavoriteFailure("Favorite failed: HTTP 400")) {
            dirtyAfterMarkSynced = true
        }
        assertTrue(dirtyAfterMarkSynced)
    }

    @Test
    fun remoteDeleteSkipsDirtyLocalEdits() {
        assertTrue(shouldSkipRemoteDelete(dirty = true, locallyDeleted = false))
        assertFalse(shouldSkipRemoteDelete(dirty = false, locallyDeleted = false))
        assertFalse(shouldSkipRemoteDelete(dirty = true, locallyDeleted = true))
    }

    @Test
    fun lastSyncTimeStaysPutWhenPullFailed() {
        assertFalse(shouldStampLastSyncTime(pullFailed = 1))
        assertTrue(shouldStampLastSyncTime(pullFailed = 0))
    }

    @Test
    fun initialPullDoesNotStoreTheSyncTokenWhenACardFails() {
        assertFalse(shouldStoreSyncToken(failed = 1))
        assertFalse(shouldStoreSyncToken(failed = 4))
        assertTrue(shouldStoreSyncToken(failed = 0))
    }

    @Test
    fun fullPullOfUnchangedContactsIsSuccess() {
        assertFalse(
            initialPullFailedToImport(
                changeCount = 4,
                pulled = 0,
                failed = 0,
                serverLinkedCount = 4,
            ),
        )
        assertTrue(
            initialPullFailedToImport(
                changeCount = 4,
                pulled = 0,
                failed = 4,
                serverLinkedCount = 4,
            ),
        )
        assertTrue(
            initialPullFailedToImport(
                changeCount = 3,
                pulled = 0,
                failed = 1,
                serverLinkedCount = 0,
            ),
        )
        assertFalse(
            initialPullFailedToImport(
                changeCount = 0,
                pulled = 0,
                failed = 0,
                serverLinkedCount = 0,
            ),
        )
        assertFalse(
            initialPullFailedToImport(
                changeCount = 3,
                pulled = 2,
                failed = 1,
                serverLinkedCount = 0,
            ),
        )
    }

    @Test
    fun blankRemoteUidIsFilledAndAnExistingUidIsKept() {
        assertEquals("uid-1", remoteUidForPush("uid-1", "new-uid"))
        assertEquals("uid-1", remoteUidForPush("  uid-1  ", "new-uid"))
        assertEquals("new-uid", remoteUidForPush(null, "new-uid"))
        assertEquals("new-uid", remoteUidForPush("   ", "new-uid"))
    }

    @Test
    fun detailNotesAndMessengerLinkAskForUploadFailureNotice() {
        assertTrue(detailFieldSaveNotifiesOnUploadFailure())
    }

    @Test
    fun remoteIndexMatchesEquivalentUrlsFromOneMap() {
        val server = "https://cloud.example"
        val contact = ContactEntity(
            id = 4,
            remoteUrl = "/remote.php/dav/addressbooks/users/a/contacts/ada%20lovelace.vcf",
            remoteUid = "uid-ada",
        )
        val index = RemoteContactIndex.build(server, listOf(contact))
        assertEquals(
            4L,
            index.find(
                server,
                "https://cloud.example/remote.php/dav/addressbooks/users/a/contacts/ada%20lovelace.vcf",
            )?.id,
        )
        assertEquals(
            4L,
            index.find(server, "/remote.php/dav/addressbooks/users/a/contacts/ada lovelace.vcf")?.id,
        )
        assertNull(index.find(server, "/remote.php/dav/addressbooks/users/a/contacts/other.vcf"))
    }

    @Test
    fun favoriteSyncErrorRevertsTheStar() {
        assertTrue(shouldRevertFavorite(SyncManager.SyncResult.Error("Could not sync favorite")))
        assertTrue(shouldRevertFavorite(SyncManager.SyncResult.NotLoggedIn))
        assertFalse(shouldRevertFavorite(SyncManager.SyncResult.Success()))
    }

    @Test
    fun favoriteToggleIsDirtyAndUploadUsesThePassedStar() {
        val toggled = contactAfterFavoriteToggle(
            ContactEntity(id = 7, isFavorite = false, dirty = false, lastModified = 4),
            now = 40,
        )
        assertTrue(toggled.isFavorite)
        assertTrue(toggled.dirty)
        assertEquals(40L, toggled.lastModified)

        // A sync that overlaps can write the old star back. The upload still sends the toggle.
        assertTrue(favoriteToUpload(passedFavorite = toggled.isFavorite, storedFavorite = false))
        assertFalse(favoriteToUpload(passedFavorite = false, storedFavorite = true))

        val reverted = contactWithFavorite(
            contact = toggled.copy(isFavorite = true, dirty = false),
            isFavorite = false,
            markDirty = true,
            now = 50,
        )
        assertFalse(reverted.isFavorite)
        assertTrue(reverted.dirty)
        assertEquals(50L, reverted.lastModified)

        val fromServer = contactWithFavorite(
            contact = ContactEntity(id = 7, isFavorite = false, dirty = false, lastModified = 1),
            isFavorite = true,
            markDirty = false,
            now = 60,
        )
        assertTrue(fromServer.isFavorite)
        assertFalse(fromServer.dirty)
        assertEquals(1L, fromServer.lastModified)
    }

    @Test
    fun syncReport404IsARemovedCardNotAFailedDownload() {
        // "HTTP/1.1 404 Not Found".substringAfter(" ") is "1.1 404 Not Found", not 404.
        assertEquals(404, davStatusCode("HTTP/1.1 404 Not Found"))
        assertEquals(200, davStatusCode("HTTP/1.1 200 OK"))
        assertEquals(204, davStatusCode("HTTP/2 204"))
        assertNull(davStatusCode(""))
        assertTrue(syncReportCardIsGone(status = 404, etag = null))
        assertTrue(syncReportCardIsGone(status = 404, etag = "  "))
        // A missing property inside propstat is 404 while the card etag is still present.
        assertFalse(syncReportCardIsGone(status = 404, etag = "abc"))
        assertFalse(syncReportCardIsGone(status = 200, etag = null))
    }

    @Test
    fun propstatFailureInside207IsNotAFavoriteSuccess() {
        val failed = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:nc="http://nextcloud.com/ns">
              <d:response>
                <d:href>/remote.php/dav/addressbooks/users/a/contacts/ada.vcf</d:href>
                <d:propstat>
                  <d:prop><nc:favorite/></d:prop>
                  <d:status>HTTP/1.1 403 Forbidden</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        assertEquals(403, failedPropstatCode(failed))
        assertNull(failedPropstatCode(failed.replace("403 Forbidden", "200 OK")))

        val mixed = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:propstat><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                <d:propstat><d:status>HTTP/1.1 409 Conflict</d:status></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        assertEquals(409, failedPropstatCode(mixed))
        assertNull(failedPropstatCode(""))
        assertNull(
            failedPropstatCode(
                "<d:propstat><d:status>HTTP/1.1 204 No Content</d:status></d:propstat>",
            ),
        )
    }

    @Test
    fun fingerprintIndexUsesNamePhonesAndEmailsOnly() {
        val index = FingerprintIndex.build(
            contacts = listOf(
                FingerprintContactRow(id = 1, displayName = "Ada Lovelace"),
                FingerprintContactRow(id = 2, displayName = "Ada Lovelace"),
            ),
            phonesByContact = mapOf(
                1L to listOf("+1 555-0101"),
                2L to listOf("+1 555-0101"),
            ),
            emailsByContact = mapOf(
                1L to listOf("Ada@Example.com"),
                2L to listOf("ada@example.com"),
            ),
        )
        assertEquals(
            1L,
            index.find("Ada Lovelace", listOf("+15550101"), listOf("ada@example.com")),
        )
        assertNull(index.find("Ada Lovelace", listOf("+15550199"), listOf("ada@example.com")))
        assertNull(index.find("", emptyList(), emptyList()))
    }

    @Test
    fun serverPullUpsertSkipsPerContactDeviceWork() {
        assertFalse(runDeviceIntegrationsOnServerPull())
    }

    @Test
    fun updateNotFoundKeepsLocalEditsAndBecomesCreate() {
        val missing = IllegalStateException("Update failed: HTTP 404")
        assertEquals(404, httpStatusFromClientError(missing))
        assertTrue(shouldRecreateAfterUpdateNotFound(httpStatus = 404, locallyDeleted = false))
        assertFalse(shouldRecreateAfterUpdateNotFound(httpStatus = 404, locallyDeleted = true))
        assertFalse(shouldRecreateAfterUpdateNotFound(httpStatus = 412, locallyDeleted = false))
        assertFalse(shouldRecreateAfterUpdateNotFound(httpStatus = null, locallyDeleted = false))
        assertTrue(localDeleteNotFoundIsSuccess(httpStatus = 404, locallyDeleted = true))
        assertFalse(localDeleteNotFoundIsSuccess(httpStatus = 500, locallyDeleted = true))
        assertFalse(localDeleteNotFoundIsSuccess(httpStatus = 404, locallyDeleted = false))

        val edited = ContactEntity(
            id = 3,
            remoteUrl = "/remote.php/dav/addressbooks/users/a/contacts/ada.vcf",
            etag = "abc",
            displayName = "Ada Lovelace",
            notes = "unsynced note",
            geo = "52.5;13.4",
            dirty = true,
            deleted = false,
            isFavorite = true,
        )
        val detached = contactAfterServerCardMissing(edited)
        assertNull(detached.remoteUrl)
        assertNull(detached.etag)
        assertEquals(edited.id, detached.id)
        assertEquals("Ada Lovelace", detached.displayName)
        assertEquals("unsynced note", detached.notes)
        assertEquals("52.5;13.4", detached.geo)
        assertTrue(detached.dirty)
        assertFalse(detached.deleted)
        assertTrue(detached.isFavorite)
    }

    @Test
    fun duplicateServerDeleteFailureDoesNotFailTheApply() {
        assertFalse(duplicateServerDeleteFailureFailsApply())
    }

    @Test
    fun favoriteUploadClearsDirtyOnlyWhenTheStarWasTheOnlyEdit() {
        assertTrue(
            shouldClearDirtyAfterFavoriteUpload(
                wasDirtyBeforeToggle = false,
                lastModifiedAtToggle = 40,
                lastModifiedNow = 40,
                storedFavorite = true,
                uploadedFavorite = true,
            ),
        )
        assertFalse(
            shouldClearDirtyAfterFavoriteUpload(
                wasDirtyBeforeToggle = true,
                lastModifiedAtToggle = 40,
                lastModifiedNow = 40,
                storedFavorite = true,
                uploadedFavorite = true,
            ),
        )
        assertFalse(
            shouldClearDirtyAfterFavoriteUpload(
                wasDirtyBeforeToggle = false,
                lastModifiedAtToggle = 40,
                lastModifiedNow = 55,
                storedFavorite = true,
                uploadedFavorite = true,
            ),
        )
        assertFalse(
            shouldClearDirtyAfterFavoriteUpload(
                wasDirtyBeforeToggle = false,
                lastModifiedAtToggle = 40,
                lastModifiedNow = 40,
                storedFavorite = false,
                uploadedFavorite = true,
            ),
        )
    }

    @Test
    fun isoAndBasicDatesShareOneParse() {
        assertEquals(LocalDate.of(2020, 1, 2), parseIsoOrBasicLocalDate("2020-01-02"))
        assertEquals(LocalDate.of(2020, 1, 2), parseIsoOrBasicLocalDate("20200102"))
        assertNull(parseIsoOrBasicLocalDate("--01-02"))
        assertNull(parseIsoOrBasicLocalDate("2020-01"))
        assertNull(parseIsoOrBasicLocalDate("2020"))
        assertNull(parseIsoOrBasicLocalDate("2020-13-40"))
    }
}
