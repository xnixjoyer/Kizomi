package com.anisync.android.presentation.settings.provider

import com.anisync.android.data.provider.ProviderSessionCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface ProviderAccountActionCoordinator {
    suspend fun disconnectAndDeleteLocalData()
    suspend fun prepareProviderChange()
}

@Singleton
class DefaultProviderAccountActionCoordinator @Inject constructor(
    private val coordinator: ProviderSessionCoordinator,
) : ProviderAccountActionCoordinator {
    override suspend fun disconnectAndDeleteLocalData() {
        coordinator.disconnectAndDeleteAllLocalProviderData()
    }

    override suspend fun prepareProviderChange() {
        coordinator.prepareDestructiveProviderChange()
    }
}

class ProviderAccountSettingsActionDispatcher @Inject constructor(
    private val coordinator: ProviderAccountActionCoordinator,
) {
    suspend fun execute(action: ProviderAccountAction) {
        when (action) {
            ProviderAccountAction.DISCONNECT_AND_DELETE_LOCAL_DATA ->
                coordinator.disconnectAndDeleteLocalData()
            ProviderAccountAction.CHANGE_PROVIDER -> coordinator.prepareProviderChange()
            ProviderAccountAction.REVOKE_MAL_CONSENT -> Unit
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderAccountSettingsModule {
    @Binds
    abstract fun bindProviderAccountActionCoordinator(
        implementation: DefaultProviderAccountActionCoordinator,
    ): ProviderAccountActionCoordinator
}
