package com.yagay.ysuite.feature.template.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object TemplateFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(
        id = "template",
        route = "template",
        order = 0,
    )

    override val startRoute = RouteId("template")
    override val routes = setOf(startRoute)
}
