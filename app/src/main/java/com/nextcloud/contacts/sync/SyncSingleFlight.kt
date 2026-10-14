package com.nextcloud.contacts.sync

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One CardDAV cursor at a time. A second [coalesce] joins the run already in
 * flight instead of starting another pull. A full pull does not join an
 * incremental run. Nested calls on this coroutine run inline; [Mutex] is not reentrant.
 */
class SyncSingleFlight {
    private val stateMutex = Mutex()
    private val workMutex = Mutex()
    private var inFlight: CompletableDeferred<SyncManager.SyncResult>? = null
    private var inFlightIsFullPull: Boolean = false

    private class AlreadyInsideSync : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
        companion object Key : kotlin.coroutines.CoroutineContext.Key<AlreadyInsideSync>
    }

    suspend fun coalesce(
        fullPull: Boolean,
        block: suspend () -> SyncManager.SyncResult,
    ): SyncManager.SyncResult {
        if (coroutineContext[AlreadyInsideSync] != null) return block()
        while (true) {
            when (val decision = decide(fullPull)) {
                is Decision.Join -> return decision.deferred.await()
                is Decision.Wait -> decision.deferred.await()
                is Decision.Lead -> return lead(decision.deferred, block)
            }
        }
    }

    /** Waits out an in-flight sync, then runs. Inline when already inside [coalesce]. */
    suspend fun exclude(block: suspend () -> SyncManager.SyncResult): SyncManager.SyncResult {
        if (coroutineContext[AlreadyInsideSync] != null) return block()
        return workMutex.withLock {
            withContext(AlreadyInsideSync()) { block() }
        }
    }

    private suspend fun decide(fullPull: Boolean): Decision = stateMutex.withLock {
        val current = inFlight?.takeIf { it.isActive }
        when {
            current != null && (inFlightIsFullPull || !fullPull) -> Decision.Join(current)
            current != null -> Decision.Wait(current)
            else -> {
                val created = CompletableDeferred<SyncManager.SyncResult>()
                inFlight = created
                inFlightIsFullPull = fullPull
                Decision.Lead(created)
            }
        }
    }

    private suspend fun lead(
        deferred: CompletableDeferred<SyncManager.SyncResult>,
        block: suspend () -> SyncManager.SyncResult,
    ): SyncManager.SyncResult {
        try {
            val result = workMutex.withLock {
                withContext(AlreadyInsideSync()) { block() }
            }
            deferred.complete(result)
            return result
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            stateMutex.withLock {
                if (inFlight === deferred) inFlight = null
            }
        }
    }

    private sealed class Decision {
        class Join(val deferred: CompletableDeferred<SyncManager.SyncResult>) : Decision()
        class Wait(val deferred: CompletableDeferred<SyncManager.SyncResult>) : Decision()
        class Lead(val deferred: CompletableDeferred<SyncManager.SyncResult>) : Decision()
    }
}
