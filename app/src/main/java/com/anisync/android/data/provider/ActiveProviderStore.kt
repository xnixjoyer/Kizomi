package com.anisync.android.data.provider

import android.content.Context
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderRuntimeState
import com.anisync.android.domain.provider.ProviderStateMachine
import com.anisync.android.domain.provider.ProviderTransitionPhase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveProviderStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<ProviderRuntimeState> = _state.asStateFlow()

    fun snapshot(): ProviderRuntimeState = _state.value

    fun reconcileLocalAccounts(
        hasAniListAccount: Boolean,
        hasMalAccount: Boolean,
    ): ProviderRuntimeState = update { current ->
        ProviderStateMachine.reconcile(current, hasAniListAccount, hasMalAccount)
    }

    fun beginLogin(provider: ActiveProvider): ProviderRuntimeState = update { current ->
        ProviderStateMachine.beginLogin(current, provider)
    }

    fun completeLogin(provider: ActiveProvider): ProviderRuntimeState = update { current ->
        ProviderStateMachine.completeLogin(current, provider)
    }

    fun cancelLogin(): ProviderRuntimeState = update { current ->
        if (current.transitionPhase == ProviderTransitionPhase.AUTHENTICATING) {
            ProviderStateMachine.cancelLogin(current)
        } else {
            current
        }
    }

    fun requireLegacySelection(): ProviderRuntimeState = persist(
        ProviderRuntimeState(transitionPhase = ProviderTransitionPhase.LEGACY_SELECTION_REQUIRED)
    )

    fun beginPurge(): ProviderRuntimeState = persist(ProviderStateMachine.beginPurge())

    fun activateExisting(provider: ActiveProvider): ProviderRuntimeState {
        require(provider != ActiveProvider.UNCONFIGURED)
        return persist(ProviderRuntimeState(activeProvider = provider))
    }

    fun finishPurge(): ProviderRuntimeState = persist(ProviderStateMachine.finishPurge())

    fun resetToUnconfigured(): ProviderRuntimeState = persist(ProviderRuntimeState())

    private fun update(transform: (ProviderRuntimeState) -> ProviderRuntimeState): ProviderRuntimeState =
        synchronized(lock) { persistLocked(transform(_state.value)) }

    private fun persist(state: ProviderRuntimeState): ProviderRuntimeState =
        synchronized(lock) { persistLocked(state) }

    private fun persistLocked(state: ProviderRuntimeState): ProviderRuntimeState {
        val committed = preferences.edit()
            .putString(KEY_ACTIVE_PROVIDER, state.activeProvider.name)
            .putString(KEY_TRANSITION_PHASE, state.transitionPhase.name)
            .apply {
                if (state.pendingProvider == null) remove(KEY_PENDING_PROVIDER)
                else putString(KEY_PENDING_PROVIDER, state.pendingProvider.name)
            }
            .commit()
        check(committed) { "Unable to persist active-provider state" }
        _state.value = state
        return state
    }

    private fun readPersisted(): ProviderRuntimeState {
        val provider = enumValueOrDefault(
            preferences.getString(KEY_ACTIVE_PROVIDER, null),
            ActiveProvider.UNCONFIGURED,
        )
        val phase = enumValueOrDefault(
            preferences.getString(KEY_TRANSITION_PHASE, null),
            ProviderTransitionPhase.IDLE,
        )
        val pending = preferences.getString(KEY_PENDING_PROVIDER, null)
            ?.let { runCatching { ActiveProvider.valueOf(it) }.getOrNull() }
            ?.takeUnless { it == ActiveProvider.UNCONFIGURED }
        return runCatching { ProviderRuntimeState(provider, phase, pending) }
            .getOrElse { ProviderRuntimeState() }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        const val PREFERENCES_NAME = "kizomi_active_provider_v1"
        const val KEY_ACTIVE_PROVIDER = "active_provider"
        const val KEY_TRANSITION_PHASE = "transition_phase"
        const val KEY_PENDING_PROVIDER = "pending_provider"
    }
}
