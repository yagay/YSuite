package com.yagay.ysuite.feature.yfiles.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object YFilesFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "yfiles",
        route = "yfiles",
        order = 100,
    )

    override val startRoute = RouteId("yfiles")
    override val routes = setOf(startRoute)
}
