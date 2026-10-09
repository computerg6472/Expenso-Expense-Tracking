package com.spendwise.intelligence.capability

import kotlinx.coroutines.flow.Flow

interface CapabilityProvider {
    /**
     * Returns the current status of an AI capability.
     */
    fun getStatus(feature: AiFeature): Availability

    /**
     * Returns the snapshot of all AI capabilities and their status.
     */
    fun getAllStatuses(): Map<AiFeature, Availability>

    /**
     * Flow of capability status updates.
     */
    fun observeStatuses(): Flow<Map<AiFeature, Availability>>
}
