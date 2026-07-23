package com.anisync.android.domain.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderStateMachineTest {
    @Test
    fun `fresh install remains unconfigured and blocks traffic`() {
        val state = ProviderStateMachine.reconcile(ProviderRuntimeState(), false, false)
        assertEquals(ActiveProvider.UNCONFIGURED, state.activeProvider)
        assertFalse(state.providerTrafficAllowed)
    }

    @Test
    fun `one legacy account migrates to its exclusive provider`() {
        assertEquals(
            ActiveProvider.ANILIST_ONLY,
            ProviderStateMachine.reconcile(ProviderRuntimeState(), true, false).activeProvider,
        )
        assertEquals(
            ActiveProvider.MAL_ONLY,
            ProviderStateMachine.reconcile(ProviderRuntimeState(), false, true).activeProvider,
        )
    }

    @Test
    fun `two legacy accounts require blocking user selection`() {
        val state = ProviderStateMachine.reconcile(ProviderRuntimeState(), true, true)
        assertEquals(ActiveProvider.UNCONFIGURED, state.activeProvider)
        assertEquals(ProviderTransitionPhase.LEGACY_SELECTION_REQUIRED, state.transitionPhase)
        assertFalse(state.providerTrafficAllowed)
    }

    @Test
    fun `login becomes active only after matching completion`() {
        val pending = ProviderStateMachine.beginLogin(
            ProviderRuntimeState(),
            ActiveProvider.MAL_ONLY,
        )
        assertFalse(pending.providerTrafficAllowed)
        val connected = ProviderStateMachine.completeLogin(pending, ActiveProvider.MAL_ONLY)
        assertEquals(ActiveProvider.MAL_ONLY, connected.activeProvider)
        assertTrue(connected.providerTrafficAllowed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong provider cannot complete pending login`() {
        ProviderStateMachine.completeLogin(
            ProviderStateMachine.beginLogin(ProviderRuntimeState(), ActiveProvider.MAL_ONLY),
            ActiveProvider.ANILIST_ONLY,
        )
    }

    @Test
    fun `purge is fail closed and finishes unconfigured`() {
        val purging = ProviderStateMachine.beginPurge()
        assertEquals(ProviderTransitionPhase.PURGING, purging.transitionPhase)
        assertFalse(purging.providerTrafficAllowed)
        assertEquals(ProviderRuntimeState(), ProviderStateMachine.finishPurge())
    }

    @Test
    fun `process restart preserves an in progress purge`() {
        val purging = ProviderStateMachine.beginPurge()
        assertEquals(purging, ProviderStateMachine.reconcile(purging, true, true))
    }
}
