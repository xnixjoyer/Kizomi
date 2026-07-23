package com.anisync.android.domain.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingModelRedactionTest {
    @Test
    fun `tracking transport models never render account media operation or private fields`() {
        val sentinels = listOf(
            "account-private-sentinel",
            "local-media-private-sentinel",
            "operation-private-sentinel",
            "notes-private-sentinel",
            "custom-list-private-sentinel",
            "raw-provider-private-sentinel",
            "remote-revision-private-sentinel",
        )
        val desired = TrackingDesiredState(
            status = TrackingStatus.CURRENT,
            progress = 3,
            notes = sentinels[3],
            customLists = listOf(sentinels[4]),
        )
        val draft = TrackingCommandDraft(
            localMediaId = sentinels[1],
            mediaType = TrackingMediaType.ANIME,
            desired = desired,
            fields = setOf(TrackingField.STATUS, TrackingField.NOTES, TrackingField.CUSTOM_LISTS),
            providerListEntryIds = mapOf(TrackingProvider.ANILIST to 987654321L),
        )
        val command = TrackingCommand(sentinels[2], 1L, draft)
        val request = TrackingProviderRequest(
            command = command,
            provider = TrackingProvider.ANILIST,
            providerAccountId = sentinels[0],
            providerMediaId = 123456789L,
            deliveryAttempt = 1,
        )
        val target = TrackingCommandTarget(
            provider = TrackingProvider.ANILIST,
            providerAccountId = sentinels[0],
            providerMediaId = 123456789L,
        )
        val snapshot = TrackingConfirmedSnapshot(
            providerListEntryId = 987654321L,
            title = "Safe title",
            state = desired,
            rawProviderFieldsJson = sentinels[5],
            remoteRevision = sentinels[6],
        )
        val rendered = listOf(desired, draft, command, request, target, snapshot).joinToString("\n")

        sentinels.forEach { sentinel -> assertFalse(sentinel, rendered.contains(sentinel)) }
        assertFalse(rendered.contains("123456789"))
        assertFalse(rendered.contains("987654321"))
        assertTrue(rendered.contains("<redacted>"))
        assertTrue(rendered.contains("Safe title"))
    }
}
