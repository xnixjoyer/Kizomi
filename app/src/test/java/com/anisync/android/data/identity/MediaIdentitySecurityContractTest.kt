package com.anisync.android.data.identity

import com.anisync.android.data.local.entity.LocalMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityIssueEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIdentitySecurityContractTest {
    @Test
    fun `identity entities contain provider metadata but no credential or login continuation fields`() {
        val fields = listOf(
            LocalMediaIdentityEntity::class.java,
            ProviderMediaIdentityEntity::class.java,
            ProviderMediaIdentityIssueEntity::class.java,
        ).flatMap { type -> type.declaredFields.map { it.name.lowercase() } }

        assertTrue(fields.contains("localmediaid"))
        assertTrue(fields.contains("providermediaid"))
        listOf(
            "accesstoken",
            "refreshtoken",
            "clientsecret",
            "authorizationcode",
            "codeverifier",
            "oauthstate",
            "sessionid",
        ).forEach { forbidden ->
            assertFalse(fields.any { it.contains(forbidden) })
        }
    }

    @Test
    fun `local identity generator does not derive ids from provider input`() {
        val generator = UuidMediaIdentityIdGenerator()
        val first = generator.newLocalMediaId()
        val second = generator.newLocalMediaId()
        assertFalse(first.matches(Regex("[0-9]+")))
        assertFalse(second.matches(Regex("[0-9]+")))
        assertFalse(first == second)
    }
}
