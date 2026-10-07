package com.yagay.ysuite.feature.yentrycleaner.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object YEntryCleanerFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "yentrycleaner",
        route = "yentrycleaner",
        order = 440,
    )
    override val startRoute = RouteId("yentrycleaner")
    override val routes = setOf(startRoute)
}
