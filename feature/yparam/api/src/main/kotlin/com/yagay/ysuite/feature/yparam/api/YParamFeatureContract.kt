package com.yagay.ysuite.feature.yparam.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object YParamFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "yparam",
        route = "yparam",
        order = 320,
    )

    override val startRoute = RouteId("yparam")
    override val routes = setOf(startRoute)
}
