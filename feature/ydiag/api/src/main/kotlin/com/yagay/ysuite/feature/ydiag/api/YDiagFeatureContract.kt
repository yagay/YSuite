package com.yagay.ysuite.feature.ydiag.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object YDiagFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "ydiag",
        route = "ydiag",
        order = 340,
    )

    override val startRoute = RouteId("ydiag")
    override val routes = setOf(startRoute)
}
