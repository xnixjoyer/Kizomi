package com.anisync.android.presentation.provider.details

internal operator fun Map<ProviderDetailsFailure, String>.get(
    failure: ProviderDetailsFailure?,
): String? = failure?.let { get(it) }
