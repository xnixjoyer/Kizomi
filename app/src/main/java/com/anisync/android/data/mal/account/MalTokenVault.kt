@file:Suppress("DEPRECATION")

package com.anisync.android.data.mal.account

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

interface MalTokenVault {
    fun write(localAccountId: String, generation: Long, tokens: MalTokenSet): MalTokenVaultResult<String>
    fun read(reference: String): MalTokenVaultResult<MalTokenSet>
    fun delete(reference: String): MalTokenVaultResult<Unit>
    fun deleteAccount(localAccountId: String): MalTokenVaultResult<Unit>
    fun references(): MalTokenVaultResult<Set<String>>
    fun consumeInitializationReset(): Boolean
}

enum class MalTokenVaultFailureReason {
    INVALID_INPUT,
    WRITE_FAILED,
    READ_FAILED,
    MISSING_ENTRY,
    CORRUPT_ENTRY,
    DELETE_FAILED,
    KEYSTORE_RESET,
}

sealed interface MalTokenVaultResult<out T> {
    data class Success<T>(val value: T) : MalTokenVaultResult<T> {
        override fun toString(): String = "MalTokenVaultResult.Success(value=<redacted>)"
    }

    data class Failure(
        val reason: MalTokenVaultFailureReason,
        val reference: String? = null,
    ) : MalTokenVaultResult<Nothing> {
        override fun toString(): String =
            "MalTokenVaultResult.Failure(reason=${reason.name}, reference=${reference ?: "none"})"
    }
}

@Singleton
class AndroidMalTokenVault @Inject constructor(
    @ApplicationContext private val context: Context,
) : MalTokenVault {
    private val lock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var initializationReset = false

    private var secure: SharedPreferences = createSecurePreferences()

    override fun write(
        localAccountId: String,
        generation: Long,
        tokens: MalTokenSet,
    ): MalTokenVaultResult<String> = synchronized(lock) {
        if (localAccountId.isBlank() || generation <= 0L) {
            return@synchronized MalTokenVaultResult.Failure(
                reason = MalTokenVaultFailureReason.INVALID_INPUT,
            )
        }
        val reference = reference(localAccountId, generation)
        val payload = StoredTokenBundle(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAtEpochMillis = tokens.expiresAtEpochMillis,
            scopes = tokens.normalizedScopes().toList(),
            generation = generation,
            writtenAtEpochMillis = System.currentTimeMillis(),
        )
        try {
            val committed = secure.edit()
                .putString(reference, json.encodeToString(StoredTokenBundle.serializer(), payload))
                .commit()
            if (committed) {
                MalTokenVaultResult.Success(reference)
            } else {
                MalTokenVaultResult.Failure(
                    reason = MalTokenVaultFailureReason.WRITE_FAILED,
                    reference = reference,
                )
            }
        } catch (_: Throwable) {
            recoverAfterCryptoFailure()
            MalTokenVaultResult.Failure(
                reason = MalTokenVaultFailureReason.KEYSTORE_RESET,
                reference = reference,
            )
        }
    }

    override fun read(reference: String): MalTokenVaultResult<MalTokenSet> = synchronized(lock) {
        if (!isReference(reference)) {
            return@synchronized MalTokenVaultResult.Failure(
                reason = MalTokenVaultFailureReason.INVALID_INPUT,
                reference = sanitizedReference(reference),
            )
        }
        try {
            val encoded = secure.getString(reference, null)
                ?: return@synchronized MalTokenVaultResult.Failure(
                    reason = MalTokenVaultFailureReason.MISSING_ENTRY,
                    reference = reference,
                )
            val stored = runCatching {
                json.decodeFromString(StoredTokenBundle.serializer(), encoded)
            }.getOrNull() ?: return@synchronized MalTokenVaultResult.Failure(
                reason = MalTokenVaultFailureReason.CORRUPT_ENTRY,
                reference = reference,
            )
            if (stored.accessToken.isBlank() || stored.generation <= 0L) {
                return@synchronized MalTokenVaultResult.Failure(
                    reason = MalTokenVaultFailureReason.CORRUPT_ENTRY,
                    reference = reference,
                )
            }
            MalTokenVaultResult.Success(
                MalTokenSet(
                    accessToken = stored.accessToken,
                    refreshToken = stored.refreshToken,
                    expiresAtEpochMillis = stored.expiresAtEpochMillis,
                    scopes = stored.scopes.toSet(),
                )
            )
        } catch (_: Throwable) {
            recoverAfterCryptoFailure()
            MalTokenVaultResult.Failure(
                reason = MalTokenVaultFailureReason.KEYSTORE_RESET,
                reference = reference,
            )
        }
    }

    override fun delete(reference: String): MalTokenVaultResult<Unit> = synchronized(lock) {
        if (!isReference(reference)) {
            return@synchronized MalTokenVaultResult.Failure(
                reason = MalTokenVaultFailureReason.INVALID_INPUT,
                reference = sanitizedReference(reference),
            )
        }
        try {
            if (secure.edit().remove(reference).commit()) {
                MalTokenVaultResult.Success(Unit)
            } else {
                MalTokenVaultResult.Failure(
                    reason = MalTokenVaultFailureReason.DELETE_FAILED,
                    reference = reference,
                )
            }
        } catch (_: Throwable) {
            recoverAfterCryptoFailure()
            MalTokenVaultResult.Failure(
                reason = MalTokenVaultFailureReason.KEYSTORE_RESET,
                reference = reference,
            )
        }
    }

    override fun deleteAccount(localAccountId: String): MalTokenVaultResult<Unit> = synchronized(lock) {
        if (localAccountId.isBlank()) {
            return@synchronized MalTokenVaultResult.Failure(
                reason = MalTokenVaultFailureReason.INVALID_INPUT,
            )
        }
        try {
            val matching = secure.all.keys.filter { it.startsWith(referencePrefix(localAccountId)) }
            val editor = secure.edit()
            matching.forEach(editor::remove)
            if (editor.commit()) {
                MalTokenVaultResult.Success(Unit)
            } else {
                MalTokenVaultResult.Failure(
                    reason = MalTokenVaultFailureReason.DELETE_FAILED,
                )
            }
        } catch (_: Throwable) {
            recoverAfterCryptoFailure()
            MalTokenVaultResult.Failure(
                reason = MalTokenVaultFailureReason.KEYSTORE_RESET,
            )
        }
    }

    override fun references(): MalTokenVaultResult<Set<String>> = synchronized(lock) {
        try {
            MalTokenVaultResult.Success(secure.all.keys.filter(::isReference).toSet())
        } catch (_: Throwable) {
            recoverAfterCryptoFailure()
            MalTokenVaultResult.Failure(
                reason = MalTokenVaultFailureReason.KEYSTORE_RESET,
            )
        }
    }

    override fun consumeInitializationReset(): Boolean = synchronized(lock) {
        val detected = initializationReset
        initializationReset = false
        detected
    }

    private fun createMasterKey(): MasterKey =
        MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun createSecurePreferences(): SharedPreferences {
        return try {
            openSecurePreferences().also { it.all.size }
        } catch (_: Throwable) {
            resetEncryptedStore()
            initializationReset = true
            openSecurePreferences()
        }
    }

    private fun openSecurePreferences(): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS,
            createMasterKey(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun recoverAfterCryptoFailure() {
        resetEncryptedStore()
        initializationReset = true
        secure = openSecurePreferences()
    }

    private fun resetEncryptedStore() {
        runCatching { context.deleteSharedPreferences(SECURE_PREFS) }
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        }
    }

    @Serializable
    private data class StoredTokenBundle(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtEpochMillis: Long?,
        val scopes: List<String>,
        val generation: Long,
        val writtenAtEpochMillis: Long,
    ) {
        override fun toString(): String =
            "StoredTokenBundle(accessToken=<redacted>, refreshToken=${if (refreshToken == null) "absent" else "<redacted>"}, expiresAtEpochMillis=$expiresAtEpochMillis, scopeCount=${scopes.size}, generation=$generation, writtenAtEpochMillis=$writtenAtEpochMillis)"
    }

    companion object {
        const val SECURE_PREFS = "mal_token_vault"
        const val MASTER_KEY_ALIAS = "anisync_mal_token_master_key_v1"
        private const val REFERENCE_PREFIX = "bundle:"

        fun reference(localAccountId: String, generation: Long): String =
            "$REFERENCE_PREFIX$localAccountId:$generation"

        private fun referencePrefix(localAccountId: String): String =
            "$REFERENCE_PREFIX$localAccountId:"

        private fun isReference(value: String): Boolean =
            value.startsWith(REFERENCE_PREFIX) && value.count { it == ':' } >= 2

        private fun sanitizedReference(value: String): String? =
            value.takeIf { it.startsWith(REFERENCE_PREFIX) }?.take(128)
    }
}
