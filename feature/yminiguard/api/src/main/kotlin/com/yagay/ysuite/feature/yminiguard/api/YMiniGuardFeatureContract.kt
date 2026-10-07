package com.yagay.ysuite.feature.yminiguard.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object YMiniGuardFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "yminiguard",
        route = "yminiguard",
        order = 420,
    )
    override val startRoute = RouteId("yminiguard")
    override val routes = setOf(startRoute)
}
