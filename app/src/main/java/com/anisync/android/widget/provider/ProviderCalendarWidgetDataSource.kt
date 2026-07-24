package com.anisync.android.widget.provider

import com.anisync.android.data.provider.ActiveProviderStore
import com.anisync.android.domain.calendar.provider.ProviderCalendarEntry
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderTransitionPhase
import javax.inject.Inject
import javax.inject.Singleton

enum class ProviderCalendarWidgetUnavailableReason {
    UNCONFIGURED,
    PROVIDER_TRANSITION,
    NO_ACTIVE_PROVIDER_SNAPSHOT,
    STALE_SNAPSHOT,
}

sealed interface ProviderCalendarWidgetState {
    data class Content(
        val provider: ActiveProvider,
        val entries: List<ProviderCalendarEntry>,
        val generatedAtEpochMillis: Long,
    ) : ProviderCalendarWidgetState

    data class Unavailable(
        val reason: ProviderCalendarWidgetUnavailableReason,
    ) : ProviderCalendarWidgetState
}

/** Reads local active-provider data only. Opening a widget never starts provider network traffic. */
@Singleton
class ProviderCalendarWidgetDataSource internal constructor(
    private val runtimeState: () -> com.anisync.android.domain.provider.ProviderRuntimeState,
    private val snapshotStore: ProviderCalendarSnapshotStore,
    private val nowEpochMillis: () -> Long,
) {
    @Inject
    constructor(
        activeProviderStore: ActiveProviderStore,
        snapshotStore: FileProviderCalendarSnapshotStore,
    ) : this(
        runtimeState = activeProviderStore::snapshot,
        snapshotStore = snapshotStore,
        nowEpochMillis = System::currentTimeMillis,
    )

    suspend fun load(
        maxSnapshotAgeMillis: Long = DEFAULT_MAX_SNAPSHOT_AGE_MILLIS,
    ): ProviderCalendarWidgetState {
        require(maxSnapshotAgeMillis >= 0L)
        val state = runtimeState()
        if (!state.providerTrafficAllowed) {
            return ProviderCalendarWidgetState.Unavailable(
                if (state.transitionPhase != ProviderTransitionPhase.IDLE) {
                    ProviderCalendarWidgetUnavailableReason.PROVIDER_TRANSITION
                } else {
                    ProviderCalendarWidgetUnavailableReason.UNCONFIGURED
                }
            )
        }
        val snapshot = snapshotStore.read(state.activeProvider)
            ?: return ProviderCalendarWidgetState.Unavailable(
                ProviderCalendarWidgetUnavailableReason.NO_ACTIVE_PROVIDER_SNAPSHOT
            )
        if (nowEpochMillis() - snapshot.generatedAtEpochMillis > maxSnapshotAgeMillis) {
            return ProviderCalendarWidgetState.Unavailable(
                ProviderCalendarWidgetUnavailableReason.STALE_SNAPSHOT
            )
        }
        return ProviderCalendarWidgetState.Content(
            provider = state.activeProvider,
            entries = snapshot.entries,
            generatedAtEpochMillis = snapshot.generatedAtEpochMillis,
        )
    }

    companion object {
        const val DEFAULT_MAX_SNAPSHOT_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
