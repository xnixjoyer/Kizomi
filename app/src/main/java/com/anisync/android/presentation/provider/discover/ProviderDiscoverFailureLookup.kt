package com.anisync.android.presentation.provider.discover

internal operator fun Map<ProviderDiscoverFailure, String>.get(
    failure: ProviderDiscoverFailure?,
): String? = failure?.let { get(it) }
