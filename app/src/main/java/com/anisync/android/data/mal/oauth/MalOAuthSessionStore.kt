package com.anisync.android.data.mal.oauth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

enum class MalOAuthSessionStoreFailureReason {
    INVALID_INPUT,
    WRITE_FAILED,
    READ_FAILED,
    CORRUPT_ENTRY,
    SESSION_CHANGED,
    KEYSTORE_RESET,
}

sealed interface MalOAuthSessionStoreResult<out T> {
    data class Success<T>(val value: T) : MalOAuthSessionStoreResult<T> {
        override fun toString(): String = "MalOAuthSessionStoreResult.Success(value=<redacted>)"
    }

    data class Failure(
        val reason: MalOAuthSessionStoreFailureReason,
        val sessionId: String? = null,
    ) : MalOAuthSessionStoreResult<Nothing> {
        override fun toString(): String =
            "MalOAuthSessionStoreResult.Failure(reason=${reason.name}, sessionId=${sessionId ?: "none"})"
    }
}

interface MalOAuthSessionStore {
    fun save(session: MalOAuthSession): MalOAuthSessionStoreResult<Unit>
    fun read(): MalOAuthSessionStoreResult<MalOAuthSession?>
    fun consume(sessionId: String, stateHash: String): MalOAuthSessionStoreResult<Unit>
    fun clearPending(sessionId: String? = null): MalOAuthSessionStoreResult<Unit>
    fun lastConsumedStateHash(): MalOAuthSessionStoreResult<String?>
    fun consumeInitializationReset(): Boolean
}

@Singleton
class AndroidMalOAuthSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : MalOAuthSessionStore {
    private val lock = Any()
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    @Volatile
    private var initializationReset = false

    private var secure: SharedPreferences = createSecurePreferences()

    override fun save(session: MalOAuthSession): MalOAuthSessionStoreResult<Unit> = synchronized(lock) {
        try {
            val encoded = json.encodeToString(StoredSession.fromDomain(session))
            if (secure.edit().putString(KEY_PENDING_SESSION, encoded).commit()) {
                MalOAuthSessionStoreResult.Success(Unit)
            } else {
                MalOAuthSessionStoreResult.Failure(
                    reason = MalOAuthSessionStoreFailureReason.WRITE_FAILED,
                    sessionId = session.sessionId,
                )
            }
        } catch (_: Throwable) {
            recoverAfterCryptoFailure()
            MalOAuthSessionStoreResult.Failure(
                reason = MalOAuthSessionStoreFailureReason.KEYSTORE_RESET,
                sessionId = session.sessionId,
            )
        }
    }

    override fun read(): MalOAuthSessionStoreResult<MalOAuthSession?> = synchronized(lock) {
        try {
            val encoded = secure.getString(KEY_PENDING_SESSION, null)
                ?: return@synchronized MalOAuthSessionStoreResult.Success(null)
            val stored = runCatching { json.decodeFromString<StoredSession>(encoded) }.getOrNull()
                ?: return@synchronized MalOAuthSessionStoreResult.Failure(
                    reason = MalOAuthSessionStoreFailureReason.CORRUPT_ENTRY,
                )
            val session = stored.toDomain()
                ?: return@synchronized MalOAuthSessionStoreResult.Failure(
                    reason = MalOAuthSessionStoreFailureReason.CORRUPT_ENTRY,
                    sessionId = stored.sessionId.takeIf(String::isNotBlank),
                )
            MalOAuthSessionStoreResult.Success(session)
        } catch (_: Throwable) {
            recoverAfterCryptoFailure()
            MalOAuthSessionStoreResult.Failure(
                reason = MalOAuthSessionStoreFailureReason.KEYSTORE_RESET,
            )
        }
    }

    override fun consume(
        sessionId: String,
        stateHash: String,
    ): MalOAuthSessionStoreResult<Unit> = synchronized(lock) {
        if (sessionId.isBlank() || stateHash.isBlank()) {
            return@synchronized MalOAuthSessionStoreResult.Failure(
                reason = MalOAuthSessionStoreFailureReason.INVALID_INPUT,
                sessionId = sessionId.takeIf(String::isNotBlank),
            )
        }
        when (val current = readLocked()) {
            is MalOAuthSessionStoreResult.Failure -> return@synchronized current
            is MalOAuthSessionStoreResult.Success -> {
                if (current.value?.sessionId != sessionId) {
                    return@synchronized MalOAuthSessionStoreResult.Failure(
                        reason = MalOAuthSessionStoreFailureReason.SESSION_CHANGED,
                        sessionId = sessionId,
                    )
                }
            }
        }
        try {
            val committed = secure.edit()
                .remove(KEY_PENDING_SESSION)
                .putString(KEY_CONSUMED_STATE_HASH, stateHash)
                .commit()
            if (committed) {
                MalOAuthSessionStoreResult.Success(Unit)
            } else {
                MalOAuthSessionStoreResult.Failure(
                    reason = MalOAuthSessionStoreFailureReason.WRITE_FAILED,
                    sessionId = sessionId,
                )
            }
        } catch (_: Throwable) {
            recoverAfterCryptoFailure()
            MalOAuthSessionStoreResult.Failure(
                reason = MalOAuthSessionStoreFailureReason.KEYSTORE_RESET,
                sessionId = sessionId,
            )
        }
    }

    override fun clearPending(sessionId: String?): MalOAuthSessionStoreResult<Unit> = synchronized(lock) {
        if (sessionId != null) {
            when (val current = readLocked()) {
                is MalOAuthSessionStoreResult.Failure -> return@synchronized current
                is MalOAuthSessionStoreResult.Success -> {
                    if (current.value != null && current.value.sessionId != sessionId) {
                        return@synchronized MalOAuthSessionStoreResult.Failure(
                            reason = MalOAuthSessionStoreFailureReason.SESSION_CHANGED,
                            sessionId = sessionId,
                        )
                    }
                }
            }
        }
        try {
            if (secure.edit().remove(KEY_PENDING_SESSION).commit()) {
                MalOAuthSessionStoreResult.Success(Unit)
            } else {
                MalOAuthSessionStoreResult.Failure(
                    reason = MalOAuthSessionStoreFailureReason.WRITE_FAILED,
                    sessionId = sessionId,
                )
            }
        } catch (_: Throwable) {
            recoverAfterCryptoFailure()
            MalOAuthSessionStoreResult.Failure(
                reason = MalOAuthSessionStoreFailureReason.KEYSTORE_RESET,
                sessionId = sessionId,
            )
        }
    }

    override fun lastConsumedStateHash(): MalOAuthSessionStoreResult<String?> = synchronized(lock) {
        try {
            MalOAuthSessionStoreResult.Success(
                secure.getString(KEY_CONSUMED_STATE_HASH, null)?.takeIf(String::isNotBlank)
            )
        } catch (_: Throwable) {
            recoverAfterCryptoFailure()
            MalOAuthSessionStoreResult.Failure(
                reason = MalOAuthSessionStoreFailureReason.KEYSTORE_RESET,
            )
        }
    }

    override fun consumeInitializationReset(): Boolean = synchronized(lock) {
        val result = initializationReset
        initializationReset = false
        result
    }

    private fun readLocked(): MalOAuthSessionStoreResult<MalOAuthSession?> {
        val encoded = secure.getString(KEY_PENDING_SESSION, null)
            ?: return MalOAuthSessionStoreResult.Success(null)
        val stored = runCatching { json.decodeFromString<StoredSession>(encoded) }.getOrNull()
            ?: return MalOAuthSessionStoreResult.Failure(
                reason = MalOAuthSessionStoreFailureReason.CORRUPT_ENTRY,
            )
        return stored.toDomain()?.let { MalOAuthSessionStoreResult.Success(it) }
            ?: MalOAuthSessionStoreResult.Failure(
                reason = MalOAuthSessionStoreFailureReason.CORRUPT_ENTRY,
                sessionId = stored.sessionId.takeIf(String::isNotBlank),
            )
    }

    private fun createSecurePreferences(): SharedPreferences = try {
        openSecurePreferences().also { it.all.size }
    } catch (_: Throwable) {
        resetEncryptedStore()
        initializationReset = true
        openSecurePreferences()
    }

    private fun createMasterKey(): MasterKey =
        MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

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
    private data class StoredSession(
        val sessionId: String,
        val environment: String,
        val redirectUri: String,
        val pkceMethod: String,
        val verifier: String,
        val challenge: String,
        val state: String,
        val createdAtEpochMillis: Long,
        val expiresAtEpochMillis: Long,
        val targetLocalAccountId: String?,
        val expectedTokenGeneration: Long?,
        val phase: String,
        val authorizationCode: String?,
    ) {
        fun toDomain(): MalOAuthSession? = runCatching {
            MalOAuthSession(
                sessionId = sessionId,
                environment = MalOAuthEnvironment.valueOf(environment),
                redirectUri = redirectUri,
                pkceMethod = MalPkceMethod.valueOf(pkceMethod),
                verifier = verifier,
                challenge = challenge,
                state = state,
                createdAtEpochMillis = createdAtEpochMillis,
                expiresAtEpochMillis = expiresAtEpochMillis,
                targetLocalAccountId = targetLocalAccountId,
                expectedTokenGeneration = expectedTokenGeneration,
                phase = MalOAuthSessionPhase.valueOf(phase),
                authorizationCode = authorizationCode,
            )
        }.getOrNull()

        override fun toString(): String =
            "StoredSession(sessionId=$sessionId, environment=$environment, redirectUri=<redacted>, pkceMethod=$pkceMethod, verifier=<redacted>, challenge=<redacted>, state=<redacted>, createdAtEpochMillis=$createdAtEpochMillis, expiresAtEpochMillis=$expiresAtEpochMillis, targetLocalAccountId=${targetLocalAccountId ?: "none"}, expectedTokenGeneration=${expectedTokenGeneration ?: "none"}, phase=$phase, authorizationCode=${if (authorizationCode == null) "absent" else "<redacted>"})"

        companion object {
            fun fromDomain(session: MalOAuthSession): StoredSession = StoredSession(
                sessionId = session.sessionId,
                environment = session.environment.name,
                redirectUri = session.redirectUri,
                pkceMethod = session.pkceMethod.name,
                verifier = session.verifier,
                challenge = session.challenge,
                state = session.state,
                createdAtEpochMillis = session.createdAtEpochMillis,
                expiresAtEpochMillis = session.expiresAtEpochMillis,
                targetLocalAccountId = session.targetLocalAccountId,
                expectedTokenGeneration = session.expectedTokenGeneration,
                phase = session.phase.name,
                authorizationCode = session.authorizationCode,
            )
        }
    }

    companion object {
        const val SECURE_PREFS = "mal_oauth_session"
        const val MASTER_KEY_ALIAS = "anisync_mal_oauth_session_key_v1"
        private const val KEY_PENDING_SESSION = "pending_session"
        private const val KEY_CONSUMED_STATE_HASH = "consumed_state_hash"
    }
}
