package com.anisync.android.data.tracking

import com.anisync.android.data.local.dao.TrackingReconciliationDao
import javax.inject.Inject
import javax.inject.Singleton

/** Finds persisted plans after process recreation without recalculating their immutable preview. */
@Singleton
class TrackingReconciliationPlanLocator @Inject constructor(
    private val dao: TrackingReconciliationDao,
) {
    suspend fun latestPlanId(): String? = dao.getLatestPlan()?.planId
}
