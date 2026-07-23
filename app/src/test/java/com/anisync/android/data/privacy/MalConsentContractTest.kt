package com.anisync.android.data.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MalConsentContractTest {
    @Test
    fun `policy version is explicit and date versioned`() {
        assertEquals("2026-07-23", MalConsentStore.CURRENT_POLICY_VERSION)
    }

    @Test
    fun `a changed policy version invalidates prior consent by contract`() {
        val prior = MalConsentRecord("2026-01-01", 1L)
        assertNotEquals(MalConsentStore.CURRENT_POLICY_VERSION, prior.policyVersion)
    }
}
