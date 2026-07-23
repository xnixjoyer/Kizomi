package com.anisync.android.data.tracking

import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.LocalMediaType
import com.anisync.android.data.identity.MediaIdentityMappingSource
import com.anisync.android.data.identity.MediaIdentityProvider
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.data.identity.MediaIdentityVerificationStatus
import com.anisync.android.data.identity.ProviderMediaIdentity
import com.anisync.android.domain.tracking.PerMediaTrackingPolicy
import com.anisync.android.domain.tracking.ProviderNetworkPolicy
import com.anisync.android.domain.tracking.TrackingCommandDraft
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

class TrackingNetworkNullTest {
    @Test
    fun `default pure AniList mode never consults MAL and targets AniList once`() = runTest {
        var malConfigurationReads = 0
        var malIdentityReads = 0
        var malAccountReads = 0
        val captured = mutableListOf<List<TrackingCommandTarget>>()
        val service = service(
            policy = PerMediaTrackingPolicy(),
            isMalConfigured = {
                malConfigurationReads++
                false
            },
            providerIdentities = {
                malIdentityReads++
                MediaIdentityResult.Success(emptyList())
            },
            activeMalAccountId = {
                malAccountReads++
                null
            },
            captured = captured,
        )

        TrackingMediaType.entries.forEach { mediaType ->
            service.enqueueAniList(input(mediaType, 40 + mediaType.ordinal))
        }

        assertEquals(2, captured.size)
        captured.forEach { targets ->
            assertEquals(listOf(TrackingProvider.ANILIST), targets.map { it.provider })
            assertEquals(null, targets.single().blocker)
        }
        assertEquals(0, malConfigurationReads)
        assertEquals(0, malIdentityReads)
        assertEquals(0, malAccountReads)
    }

    @Test
    fun `MAL-only produces zero AniList targets and AniList-only produces zero MAL targets`() = runTest {
        val malOnly = mutableListOf<List<TrackingCommandTarget>>()
        service(
            policy = PerMediaTrackingPolicy(
                animeMode = TrackingMode.MYANIMELIST_ONLY,
                mangaMode = TrackingMode.MYANIMELIST_ONLY,
            ),
            captured = malOnly,
        ).also { service ->
            TrackingMediaType.entries.forEach { type ->
                service.enqueueAniList(input(type, 50 + type.ordinal))
            }
        }
        assertTrue(malOnly.flatten().none { it.provider == TrackingProvider.ANILIST })
        assertTrue(malOnly.all { it.single().provider == TrackingProvider.MYANIMELIST })

        val aniListOnly = mutableListOf<List<TrackingCommandTarget>>()
        service(policy = PerMediaTrackingPolicy(), captured = aniListOnly).also { service ->
            TrackingMediaType.entries.forEach { type ->
                service.enqueueAniList(input(type, 60 + type.ordinal))
            }
        }
        assertTrue(aniListOnly.flatten().none { it.provider == TrackingProvider.MYANIMELIST })
        assertTrue(aniListOnly.all { it.single().provider == TrackingProvider.ANILIST })
    }

    @Test
    fun `dual mode creates exactly one independently account-bound target per provider`() = runTest {
        val captured = mutableListOf<List<TrackingCommandTarget>>()
        service(
            policy = PerMediaTrackingPolicy(
                animeMode = TrackingMode.DUAL,
                mangaMode = TrackingMode.DUAL,
            ),
            captured = captured,
        ).enqueueAniList(input(TrackingMediaType.ANIME, 70))

        val targets = captured.single()
        assertEquals(2, targets.size)
        assertEquals(1, targets.count { it.provider == TrackingProvider.ANILIST })
        assertEquals(1, targets.count { it.provider == TrackingProvider.MYANIMELIST })
        assertEquals("ani-account", targets.single { it.provider == TrackingProvider.ANILIST }.providerAccountId)
        assertEquals("mal-account", targets.single { it.provider == TrackingProvider.MYANIMELIST }.providerAccountId)
    }

    @Test
    fun `disabled AniList gate persists an explicit blocker before worker delivery`() = runTest {
        val captured = mutableListOf<List<TrackingCommandTarget>>()
        service(
            policy = PerMediaTrackingPolicy(),
            network = ProviderNetworkPolicy(allowAniList = false),
            captured = captured,
        ).enqueueAniList(input(TrackingMediaType.ANIME, 80))

        assertEquals(TrackingFailureKind.NETWORK_BLOCKED, captured.single().single().blocker)
    }

    @Test
    fun `provider lookup cancellation remains control flow`() = runTest {
        var propagated = false
        try {
            service(
                policy = PerMediaTrackingPolicy(animeMode = TrackingMode.MYANIMELIST_ONLY),
                providerIdentities = { throw CancellationException("obsolete session") },
                captured = mutableListOf(),
            ).enqueueAniList(input(TrackingMediaType.ANIME, 90))
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }

    private fun service(
        policy: PerMediaTrackingPolicy,
        network: ProviderNetworkPolicy = ProviderNetworkPolicy(),
        isMalConfigured: () -> Boolean = { true },
        providerIdentities: suspend (String) -> MediaIdentityResult<List<ProviderMediaIdentity>> =
            { localId ->
                MediaIdentityResult.Success(
                    listOf(
                        ProviderMediaIdentity(
                            id = 1,
                            localMediaId = localId,
                            provider = MediaIdentityProvider.MYANIMELIST,
                            providerMediaId = 700,
                            mediaType = LocalMediaType.ANIME,
                            mappingSource = MediaIdentityMappingSource.ANILIST_ID_MAL,
                            verificationStatus = MediaIdentityVerificationStatus.EXACT,
                            createdAtEpochMillis = 1,
                            updatedAtEpochMillis = 1,
                        )
                    )
                )
            },
        activeMalAccountId: suspend () -> String? = { "mal-account" },
        captured: MutableList<List<TrackingCommandTarget>>,
    ) = TrackingCommandService(
        ensureLocalIdentity = { type, mediaId ->
            MediaIdentityResult.Success(LocalMediaIdentity("local-$mediaId", type, 1, 1))
        },
        activeAniListAccountId = { "ani-account" },
        getProviderIdentities = providerIdentities,
        activeMalAccountId = activeMalAccountId,
        routingPolicy = { policy },
        providerNetworkPolicy = { network },
        isMalConfigured = isMalConfigured,
        enqueueCommand = { _: TrackingCommandDraft, targets: List<TrackingCommandTarget> ->
            captured += targets
            TrackingEnqueueResult.Accepted(
                TrackingEnqueueReceipt(
                    operationId = "operation-${captured.size}",
                    generation = captured.size.toLong(),
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

    private fun input(mediaType: TrackingMediaType, mediaId: Int) = AniListTrackingCommandInput(
        aniListMediaId = mediaId,
        mediaType = mediaType,
        desired = TrackingDesiredState(status = TrackingStatus.CURRENT, progress = 1),
        fields = setOf(TrackingField.STATUS, TrackingField.PROGRESS),
    )
}
