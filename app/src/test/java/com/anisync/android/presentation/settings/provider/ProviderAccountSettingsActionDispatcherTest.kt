package com.anisync.android.presentation.settings.provider

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderAccountSettingsActionDispatcherTest {
    @Test
    fun `disconnect and provider change delegate to safe coordinator`() = runTest {
        val coordinator = RecordingCoordinator()
        val dispatcher = ProviderAccountSettingsActionDispatcher(coordinator)

        dispatcher.execute(ProviderAccountAction.DISCONNECT_AND_DELETE_LOCAL_DATA)
        dispatcher.execute(ProviderAccountAction.CHANGE_PROVIDER)
        dispatcher.execute(ProviderAccountAction.REVOKE_MAL_CONSENT)

        assertEquals(1, coordinator.disconnectCalls)
        assertEquals(1, coordinator.providerChangeCalls)
    }

    private class RecordingCoordinator : ProviderAccountActionCoordinator {
        var disconnectCalls = 0
        var providerChangeCalls = 0

        override suspend fun disconnectAndDeleteLocalData() {
            disconnectCalls += 1
        }

        override suspend fun prepareProviderChange() {
            providerChangeCalls += 1
        }
    }
}
