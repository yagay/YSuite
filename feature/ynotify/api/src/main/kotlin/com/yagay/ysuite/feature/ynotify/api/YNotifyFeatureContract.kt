package com.yagay.ysuite.feature.ynotify.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object YNotifyFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "ynotify",
        route = "ynotify",
        order = 380,
    )

    override val startRoute = RouteId("ynotify")
    override val routes = setOf(startRoute)
}
