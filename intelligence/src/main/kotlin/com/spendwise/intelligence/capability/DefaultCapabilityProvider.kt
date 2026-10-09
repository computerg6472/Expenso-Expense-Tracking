package com.spendwise.intelligence.capability

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCapabilityProvider @Inject constructor() : CapabilityProvider {

    private val capabilityMap = ConcurrentHashMap<AiFeature, Availability>().apply {
        // Default all features to FALLBACK_ONLY initially
        // As local on-device models are initialized or detected, they can be upgraded to AVAILABLE
        AiFeature.entries.forEach { feature ->
            put(feature, Availability.FALLBACK_ONLY)
        }
    }

    private val _statuses = MutableStateFlow<Map<AiFeature, Availability>>(capabilityMap.toMap())
    val statuses = _statuses.asStateFlow()

    override fun getStatus(feature: AiFeature): Availability {
        return capabilityMap[feature] ?: Availability.FALLBACK_ONLY
    }

    override fun getAllStatuses(): Map<AiFeature, Availability> {
        return capabilityMap.toMap()
    }

    override fun observeStatuses(): Flow<Map<AiFeature, Availability>> {
        return statuses
    }

    /**
     * Updates the status of a specific AI feature.
     */
    fun updateStatus(feature: AiFeature, availability: Availability) {
        capabilityMap[feature] = availability
        _statuses.value = capabilityMap.toMap()
    }
}
