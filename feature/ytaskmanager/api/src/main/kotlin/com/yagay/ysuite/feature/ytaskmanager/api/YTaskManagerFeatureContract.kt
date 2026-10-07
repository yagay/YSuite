package com.yagay.ysuite.feature.ytaskmanager.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object YTaskManagerFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "ytaskmanager",
        route = "ytaskmanager",
        order = 300,
    )

    override val startRoute = RouteId("ytaskmanager")
    override val routes = setOf(startRoute)
}
