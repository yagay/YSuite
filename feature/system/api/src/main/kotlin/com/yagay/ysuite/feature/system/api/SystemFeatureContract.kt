package com.yagay.ysuite.feature.system.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object SystemFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "system",
        route = "system",
        order = 10,
    )

    override val startRoute = RouteId("system")
    override val routes = setOf(startRoute)
}
