package com.yagay.ysuite.navigation

import com.yagay.ysuite.model.FeatureDescriptor

@JvmInline
value class RouteId(val value: String)

interface FeatureRegistration {
    val descriptor: FeatureDescriptor
}

interface FeatureRegistry {
    val features: List<FeatureRegistration>
}
