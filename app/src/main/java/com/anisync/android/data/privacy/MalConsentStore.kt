package com.anisync.android.data.privacy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class MalConsentRecord(
    val policyVersion: String,
    val acceptedAtEpochMillis: Long,
)

@Singleton
class MalConsentStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _record = MutableStateFlow(read())
    val record: StateFlow<MalConsentRecord?> = _record.asStateFlow()

    fun isCurrentConsentAccepted(): Boolean = _record.value?.policyVersion == CURRENT_POLICY_VERSION

    fun accept(nowEpochMillis: Long = System.currentTimeMillis()): MalConsentRecord {
        require(nowEpochMillis > 0L)
        val committed = preferences.edit()
            .putString(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
            .putLong(KEY_ACCEPTED_AT, nowEpochMillis)
            .commit()
        check(committed) { "Unable to persist MyAnimeList policy consent" }
        return MalConsentRecord(CURRENT_POLICY_VERSION, nowEpochMillis).also { _record.value = it }
    }

    fun revoke() {
        check(preferences.edit().clear().commit()) { "Unable to revoke MyAnimeList policy consent" }
        _record.value = null
    }

    private fun read(): MalConsentRecord? {
        val version = preferences.getString(KEY_POLICY_VERSION, null) ?: return null
        val timestamp = preferences.getLong(KEY_ACCEPTED_AT, 0L)
        return if (timestamp > 0L) MalConsentRecord(version, timestamp) else null
    }

    companion object {
        const val CURRENT_POLICY_VERSION = "2026-07-23"
        private const val PREFERENCES_NAME = "kizomi_mal_consent_v1"
        private const val KEY_POLICY_VERSION = "policy_version"
        private const val KEY_ACCEPTED_AT = "accepted_at_epoch_millis"
    }
}
