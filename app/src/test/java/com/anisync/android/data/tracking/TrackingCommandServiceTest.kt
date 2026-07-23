package com.anisync.android.data.tracking

import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.LocalMediaType
import com.anisync.android.data.identity.MediaIdentityMappingSource
import com.anisync.android.data.identity.MediaIdentityProvider
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.data.identity.MediaIdentityVerificationStatus
import com.anisync.android.data.identity.ProviderMediaIdentity
import com.anisync.android.domain.tracking.PerMediaTrackingPolicy
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingCommand
import com.anisync.android.domain.tracking.TrackingCommandTarget
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueReceipt
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingMode
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.TrackingTargetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingCommandServiceTest {
    @Test
    fun `full AniList edit crosses the durable boundary exactly once with absolute state`() = runTest {
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val service = service(enqueued = enqueued)
        val desired = TrackingDesiredState(
            status = TrackingStatus.CURRENT,
            progress = 7,
            score100 = 82.0,
            repeatCount = 2,
            notes = "private note",
            startedAt = "2026-01-02",
            customLists = listOf("Favorites"),
            isPrivate = true,
            hiddenFromStatusLists = true,
        )

        val result = service.enqueueAniList(
            AniListTrackingCommandInput(
                aniListMediaId = 42,
                aniListListEntryId = 900,
                mediaType = TrackingMediaType.ANIME,
                desired = desired,
                fields = setOf(
                    TrackingField.STATUS,
                    TrackingField.PROGRESS,
                    TrackingField.SCORE,
                    TrackingField.REPEAT_COUNT,
                    TrackingField.NOTES,
                    TrackingField.STARTED_AT,
                    TrackingField.CUSTOM_LISTS,
                    TrackingField.PRIVATE,
                    TrackingField.HIDDEN_FROM_STATUS_LISTS,
                ),
            )
        )

        assertTrue(result is TrackingEnqueueResult.Accepted)
        assertEquals(1, enqueued.size)
        val (draft, targets) = enqueued.single()
        assertEquals("local-42", draft.localMediaId)
        assertEquals(desired, draft.desired)
        assertEquals(900L, draft.providerListEntryIds[TrackingProvider.ANILIST])
        assertEquals(listOf(TrackingProvider.ANILIST), targets.map { it.provider })
        assertEquals("7", targets.single().providerAccountId)
        assertEquals(42L, targets.single().providerMediaId)
        assertEquals(null, targets.single().blocker)
    }

    @Test
    fun `missing account and missing delete handle are persisted as explicit blockers`() = runTest {
        val withoutAccount = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        service(accountId = null, enqueued = withoutAccount).enqueueAniList(
            input(delete = false)
        )
        assertEquals(
            TrackingFailureKind.MISSING_ACCOUNT,
            withoutAccount.single().second.single().blocker,
        )

        val missingDeleteHandle = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        service(enqueued = missingDeleteHandle).enqueueAniList(input(delete = true))
        assertEquals(
            TrackingFailureKind.MISSING_IDENTITY,
            missingDeleteHandle.single().second.single().blocker,
        )
        assertTrue(missingDeleteHandle.single().first.deleteIntent)
    }

    @Test
    fun `identity failure rejects before enqueue and cancellation remains control flow`() = runTest {
        var enqueueCount = 0
        val rejected = TrackingCommandService(
            ensureLocalIdentity = { _, _ -> MediaIdentityResult.StorageFailure("test") },
            activeAniListAccountId = { "7" },
            enqueueCommand = { _, _ ->
                enqueueCount++
                accepted()
            },
        ).enqueueAniList(input(delete = false))
        assertEquals(TrackingFailureKind.STORAGE, (rejected as TrackingEnqueueResult.Rejected).reason)
        assertEquals(0, enqueueCount)

        var cancelled = false
        try {
            TrackingCommandService(
                ensureLocalIdentity = { _, _ -> throw CancellationException("obsolete") },
                activeAniListAccountId = { "7" },
                enqueueCommand = { _, _ -> accepted() },
            ).enqueueAniList(input(delete = false))
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun `serialized command retains provider handle and AniList-only compatibility fields`() {
        val draft = TrackingCommandDraft(
            localMediaId = "local-42",
            mediaType = TrackingMediaType.MANGA,
            desired = TrackingDesiredState(
                status = TrackingStatus.PAUSED,
                progress = 12,
                customLists = listOf("Owned"),
                isPrivate = true,
                hiddenFromStatusLists = true,
            ),
            fields = setOf(
                TrackingField.STATUS,
                TrackingField.PROGRESS,
                TrackingField.CUSTOM_LISTS,
                TrackingField.PRIVATE,
                TrackingField.HIDDEN_FROM_STATUS_LISTS,
            ),
            providerListEntryIds = mapOf(TrackingProvider.ANILIST to 901L),
        )
        val codec = TrackingCommandCodec()
        val decoded = codec.decode(codec.encode(TrackingCommand("operation", 1L, draft)))

        assertEquals(draft, decoded.draft)
        assertEquals(901L, decoded.draft.providerListEntryIds[TrackingProvider.ANILIST])
        assertEquals(listOf("Owned"), decoded.draft.desired.customLists)
    }

    @Test
    fun `anime and manga policies route independently across every configured mode`() = runTest {
        val cases = listOf(
            PerMediaTrackingPolicy() to mapOf(
                TrackingMediaType.ANIME to listOf(TrackingProvider.ANILIST),
                TrackingMediaType.MANGA to listOf(TrackingProvider.ANILIST),
            ),
            PerMediaTrackingPolicy(
                animeMode = TrackingMode.ANILIST_ONLY,
                mangaMode = TrackingMode.MYANIMELIST_ONLY,
            ) to mapOf(
                TrackingMediaType.ANIME to listOf(TrackingProvider.ANILIST),
                TrackingMediaType.MANGA to listOf(TrackingProvider.MYANIMELIST),
            ),
            PerMediaTrackingPolicy(
                animeMode = TrackingMode.MYANIMELIST_ONLY,
                mangaMode = TrackingMode.ANILIST_ONLY,
            ) to mapOf(
                TrackingMediaType.ANIME to listOf(TrackingProvider.MYANIMELIST),
                TrackingMediaType.MANGA to listOf(TrackingProvider.ANILIST),
            ),
            PerMediaTrackingPolicy(
                animeMode = TrackingMode.MYANIMELIST_ONLY,
                mangaMode = TrackingMode.MYANIMELIST_ONLY,
            ) to mapOf(
                TrackingMediaType.ANIME to listOf(TrackingProvider.MYANIMELIST),
                TrackingMediaType.MANGA to listOf(TrackingProvider.MYANIMELIST),
            ),
            PerMediaTrackingPolicy(
                animeMode = TrackingMode.DUAL,
                mangaMode = TrackingMode.DUAL,
            ) to mapOf(
                TrackingMediaType.ANIME to listOf(
                    TrackingProvider.ANILIST,
                    TrackingProvider.MYANIMELIST,
                ),
                TrackingMediaType.MANGA to listOf(
                    TrackingProvider.ANILIST,
                    TrackingProvider.MYANIMELIST,
                ),
            ),
        )

        cases.forEach { (policy, expected) ->
            TrackingMediaType.entries.forEach { mediaType ->
                val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
                routedService(policy = policy, enqueued = enqueued).enqueueAniList(
                    input(delete = false).copy(mediaType = mediaType)
                )
                assertEquals(expected.getValue(mediaType), enqueued.single().second.map { it.provider })
                assertTrue(enqueued.single().second.all { it.blocker == null })
            }
        }
    }

    @Test
    fun `MAL configuration logout identity and account switch remain explicit`() = runTest {
        val policy = PerMediaTrackingPolicy(animeMode = TrackingMode.MYANIMELIST_ONLY)

        val unconfigured = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        routedService(
            policy = policy,
            malConfigured = false,
            enqueued = unconfigured,
        ).enqueueAniList(input(delete = false))
        assertEquals(
            TrackingFailureKind.PROVIDER_NOT_CONFIGURED,
            unconfigured.single().second.single().blocker,
        )

        val loggedOut = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        routedService(
            policy = policy,
            malAccountId = null,
            enqueued = loggedOut,
        ).enqueueAniList(input(delete = false))
        assertEquals(TrackingFailureKind.MISSING_ACCOUNT, loggedOut.single().second.single().blocker)

        val missingIdentity = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        routedService(
            policy = PerMediaTrackingPolicy(animeMode = TrackingMode.DUAL),
            malMediaId = null,
            enqueued = missingIdentity,
        ).enqueueAniList(input(delete = false))
        assertEquals(2, missingIdentity.single().second.size)
        assertEquals(
            TrackingFailureKind.MISSING_IDENTITY,
            missingIdentity.single().second.single {
                it.provider == TrackingProvider.MYANIMELIST
            }.blocker,
        )
        assertEquals(
            null,
            missingIdentity.single().second.single {
                it.provider == TrackingProvider.ANILIST
            }.blocker,
        )

        var selectedAccount = "mal-old"
        val switched = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val service = routedService(
            policy = policy,
            activeMalAccountId = { selectedAccount },
            enqueued = switched,
        )
        service.enqueueAniList(input(delete = false))
        selectedAccount = "mal-new"
        service.enqueueAniList(input(delete = false).copy(aniListMediaId = 43))
        assertEquals("mal-old", switched[0].second.single().providerAccountId)
        assertEquals("mal-new", switched[1].second.single().providerAccountId)
    }

    private fun service(
        accountId: String? = "7",
        enqueued: MutableList<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>,
    ) = TrackingCommandService(
        ensureLocalIdentity = { type, mediaId ->
            MediaIdentityResult.Success(
                LocalMediaIdentity("local-$mediaId", type, 1L, 1L)
            )
        },
        activeAniListAccountId = { accountId },
        enqueueCommand = { draft, targets ->
            enqueued += draft to targets
            accepted(targets.single().blocker)
        },
    )

    private fun routedService(
        policy: PerMediaTrackingPolicy,
        malConfigured: Boolean = true,
        malAccountId: String? = "mal-account",
        activeMalAccountId: suspend () -> String? = { malAccountId },
        malMediaId: Long? = 84L,
        enqueued: MutableList<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>,
    ) = TrackingCommandService(
        ensureLocalIdentity = { type, mediaId ->
            MediaIdentityResult.Success(LocalMediaIdentity("local-$mediaId", type, 1L, 1L))
        },
        activeAniListAccountId = { "7" },
        getProviderIdentities = { localMediaId ->
            MediaIdentityResult.Success(
                malMediaId?.let { id ->
                    listOf(
                        ProviderMediaIdentity(
                            id = 1L,
                            localMediaId = localMediaId,
                            provider = MediaIdentityProvider.MYANIMELIST,
                            providerMediaId = id,
                            mediaType = LocalMediaType.ANIME,
                            mappingSource = MediaIdentityMappingSource.ANILIST_ID_MAL,
                            verificationStatus = MediaIdentityVerificationStatus.EXACT,
                            createdAtEpochMillis = 1L,
                            updatedAtEpochMillis = 1L,
                        )
                    )
                }.orEmpty()
            )
        },
        activeMalAccountId = activeMalAccountId,
        routingPolicy = { policy },
        isMalConfigured = { malConfigured },
        enqueueCommand = { draft, targets ->
            enqueued += draft to targets
            TrackingEnqueueResult.Accepted(
                TrackingEnqueueReceipt(
                    operationId = "operation-${enqueued.size}",
                    generation = enqueued.size.toLong(),
                    deduplicated = false,
                    targetStates = targets.associate { target ->
                        target.provider to if (target.blocker == null) {
                            TrackingTargetState.PENDING
                        } else {
                            TrackingTargetState.BLOCKED
                        }
                    },
                )
            )
        },
    )

    private fun input(delete: Boolean) = AniListTrackingCommandInput(
        aniListMediaId = 42,
        mediaType = TrackingMediaType.ANIME,
        desired = TrackingDesiredState(
            status = if (delete) null else TrackingStatus.CURRENT,
            progress = 1,
        ),
        fields = setOf(if (delete) TrackingField.DELETE else TrackingField.PROGRESS),
        deleteIntent = delete,
    )

    private fun accepted(
        blocker: TrackingFailureKind? = null,
    ): TrackingEnqueueResult.Accepted = TrackingEnqueueResult.Accepted(
        TrackingEnqueueReceipt(
            operationId = "operation",
            generation = 1,
            deduplicated = false,
            targetStates = mapOf(
                TrackingProvider.ANILIST to if (blocker == null) {
                    TrackingTargetState.PENDING
                } else {
                    TrackingTargetState.BLOCKED
                }
            ),
        )
    )
}
