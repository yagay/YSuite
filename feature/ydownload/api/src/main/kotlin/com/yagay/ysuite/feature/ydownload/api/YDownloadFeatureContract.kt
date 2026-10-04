package com.yagay.ysuite.feature.ydownload.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object YDownloadFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "ydownload",
        route = "ydownload",
        order = 200,
    )

    override val startRoute = RouteId("ydownload")
    override val routes = setOf(startRoute)
}
