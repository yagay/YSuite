package com.yagay.ysuite.feature.settings.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object SettingsFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "settings",
        route = "settings",
        order = 1000,
    )

    override val startRoute = RouteId("settings")
    override val routes = setOf(startRoute)
}
