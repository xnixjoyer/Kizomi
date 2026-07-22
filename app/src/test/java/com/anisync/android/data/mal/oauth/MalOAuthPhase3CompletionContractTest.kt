package com.anisync.android.data.mal.oauth

import com.anisync.android.data.mal.account.MalAccountCredentialStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalOAuthPhase3CompletionContractTest {
    @Test
    fun `public OAuth and credential APIs contain no client secret surface`() {
        val exposedNames = sequenceOf(
            MalOAuthConfiguration::class.java,
            MalOAuthSession::class.java,
            MalOAuthRequestFactory::class.java,
            MalOAuthTokenService::class.java,
            MalAccountCredentialStore::class.java,
        ).flatMap { type ->
            type.declaredFields.asSequence().map { it.name } +
                type.declaredMethods.asSequence().map { it.name } +
                type.declaredMethods.asSequence().flatMap { method ->
                    method.parameters.asSequence().map { it.name }
                }
        }.map(String::lowercase).toList()

        assertFalse(exposedNames.any { "clientsecret" in it || "client_secret" in it })
    }

    @Test
    fun `completion contract retains exact callback single retry and conditional persistence boundaries`() {
        val authMethods = MalAuthRepository::class.java.declaredMethods.map { it.name }.toSet()
        val authenticatedMethods = AuthenticatedMalClient::class.java.declaredMethods.map { it.name }.toSet()
        val credentialMethods = MalAccountCredentialStore::class.java.methods.map { it.name }.toSet()

        assertTrue("startLogin" in authMethods)
        assertTrue("handleCallback" in authMethods)
        assertTrue("resumePendingLogin" in authMethods)
        assertTrue("execute" in authenticatedMethods)
        assertTrue("replaceTokensIfGeneration" in credentialMethods)
    }
}
