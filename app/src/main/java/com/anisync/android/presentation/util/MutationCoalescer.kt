package com.anisync.android.presentation.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Coalesces rapid, idempotent mutations (likes, favourites, ratings, progress)
 * keyed by entity id, so a burst of taps collapses into a bounded sequence of
 * absolute network writes.
 *
 * A key has at most one worker. New values replace the desired target but never
 * cancel a commit that has already started. This matters for Apollo/OkHttp calls:
 * cancelling an in-flight worker used to surface internal messages such as
 * "I49 was cancelled" and could let older/newer absolute writes race each other.
 * The worker now serializes commits and, after each result, drains the newest
 * desired value.
 *
 * If the settled value equals the last server-committed value — e.g. +1 then -1
 * on progress — nothing is sent. Optimistic UI updates happen at the call site;
 * this class only governs when and in which order the network writes occur.
 *
 * @param V the desired settled value. For toggle endpoints use [Boolean] and
 *   have [commit] issue a single toggle when the target differs from committed.
 *   For absolute endpoints use the absolute value (for example progress [Int]).
 */
class MutationCoalescer<K : Any, V>(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 500L,
    /**
     * Sends the settled [value] for [key]; returns true if it reached the server.
     * Returning false leaves the committed baseline unchanged. If a newer target
     * arrived while the failed request was running, that newer target is still
     * drained by the same worker.
     */
    private val commit: suspend (key: K, value: V) -> Boolean
) {
    private val lock = Any()
    private val jobs = mutableMapOf<K, Job>()
    private val desired = mutableMapOf<K, V>()
    private val committed = ConcurrentHashMap<K, V>()

    /**
     * Record the value currently in sync with the server for [key] so a coalesced
     * no-op can be detected. Does not overwrite an existing tracked baseline.
     */
    fun seed(key: K, value: V) {
        committed.putIfAbsent(key, value)
    }

    /** Force [key]'s committed baseline to [value] after an external refresh. */
    fun reset(key: K, value: V) {
        committed[key] = value
    }

    /** Returns whether [value] is still the newest requested value for [key]. */
    fun isLatest(key: K, value: V): Boolean = synchronized(lock) {
        desired[key] == value
    }

    /**
     * Make [target] the newest desired value for [key]. A pending debounce is
     * naturally superseded, while an already-running commit is allowed to finish.
     * Only one serialized worker exists per key.
     */
    fun submit(key: K, target: V) {
        synchronized(lock) {
            desired[key] = target
            if (jobs[key]?.isActive != true) {
                jobs[key] = scope.launch { drain(key) }
            }
        }
    }

    private suspend fun drain(key: K) {
        val worker = currentCoroutineContext()[Job] ?: return
        try {
            while (true) {
                delay(debounceMs)
                val target = synchronized(lock) { desired[key] } ?: return
                val baseline = committed[key]
                val success = if (baseline == target) {
                    true
                } else {
                    commit(key, target)
                }
                if (success && baseline != target) {
                    committed[key] = target
                }

                val hasNewerTarget = synchronized(lock) {
                    val latest = desired[key]
                    if (latest == target || latest == null) {
                        desired.remove(key)
                        if (jobs[key] === worker) jobs.remove(key)
                        false
                    } else {
                        true
                    }
                }
                if (!hasNewerTarget) return
            }
        } finally {
            synchronized(lock) {
                if (jobs[key] === worker) jobs.remove(key)
            }
        }
    }

    /**
     * Cancel pending or active work for [key] intentionally (for example after
     * entity removal). Clearing [desired] first lets callers suppress cancellation
     * as a user-facing mutation error via [isLatest].
     */
    fun cancel(key: K) {
        val job = synchronized(lock) {
            desired.remove(key)
            jobs.remove(key)
        }
        job?.cancel()
    }
}
