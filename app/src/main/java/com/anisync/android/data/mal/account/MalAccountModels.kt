package com.anisync.android.data.mal.account

enum class MalAccountProvider {
    MYANIMELIST,
}

enum class MalTokenStatus {
    ACTIVE,
    EXPIRED,
    MISSING,
    CORRUPT,
    KEYSTORE_RESET,
}

data class MalAccountProfile(
    val malUserId: Long? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
) {
    override fun toString(): String =
        "MalAccountProfile(malUserId=<redacted>, username=<redacted>, " +
            "displayName=<redacted>, avatarUrl=<redacted>)"
}

data class MalTokenSet(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtEpochMillis: Long? = null,
    val scopes: Set<String> = emptySet(),
) {
    init {
        require(accessToken.isNotBlank()) { "MAL access token must not be blank" }
        require(refreshToken == null || refreshToken.isNotBlank()) {
            "MAL refresh token must be null or non-blank"
        }
    }

    fun normalizedScopes(): Set<String> =
        scopes.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSortedSet()

    override fun toString(): String =
        "MalTokenSet(accessToken=<redacted>, " +
            "refreshToken=${if (refreshToken == null) "absent" else "<redacted>"}, " +
            "expiresAtEpochMillis=$expiresAtEpochMillis, scopeCount=${normalizedScopes().size})"
}

data class MalAccount(
    val localAccountId: String,
    val provider: MalAccountProvider,
    val profile: MalAccountProfile,
    val tokenGeneration: Long,
    val tokenExpiresAtEpochMillis: Long?,
    val scopes: Set<String>,
    val tokenStatus: MalTokenStatus,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    override fun toString(): String =
        "MalAccount(localAccountId=<redacted>, provider=${provider.name}, profile=<redacted>, " +
            "tokenGeneration=$tokenGeneration, tokenExpiresAtEpochMillis=$tokenExpiresAtEpochMillis, " +
            "scopeCount=${scopes.size}, tokenStatus=${tokenStatus.name}, isActive=$isActive, " +
            "createdAtEpochMillis=$createdAtEpochMillis, updatedAtEpochMillis=$updatedAtEpochMillis)"
}

enum class MalAccountFailureReason {
    ACCOUNT_NOT_FOUND,
    ACCOUNT_NOT_ACTIVATABLE,
    ACCOUNT_SESSION_CHANGED,
    INVALID_TOKEN_BUNDLE,
    VAULT_WRITE_FAILED,
    VAULT_DELETE_FAILED,
    TOKEN_MISSING,
    TOKEN_CORRUPT,
    KEYSTORE_RESET,
    DATABASE_WRITE_FAILED,
}

sealed interface MalAccountResult<out T> {
    data class Success<T>(val value: T) : MalAccountResult<T> {
        override fun toString(): String = "MalAccountResult.Success(value=<redacted>)"
    }

    data class Failure(
        val reason: MalAccountFailureReason,
        val localAccountId: String? = null,
        val generation: Long? = null,
    ) : MalAccountResult<Nothing> {
        override fun toString(): String =
            "MalAccountResult.Failure(reason=${reason.name}, localAccountId=<redacted>, " +
                "generation=${generation ?: "none"})"
    }
}
