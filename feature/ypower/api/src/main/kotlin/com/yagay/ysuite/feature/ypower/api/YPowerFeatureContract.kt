package com.yagay.ysuite.feature.ypower.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object YPowerFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "ypower",
        route = "ypower",
        order = 360,
    )

    override val startRoute = RouteId("ypower")
    override val routes = setOf(startRoute)
}
