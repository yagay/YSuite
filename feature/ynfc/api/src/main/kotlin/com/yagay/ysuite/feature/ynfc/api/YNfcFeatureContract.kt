package com.yagay.ysuite.feature.ynfc.api
import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId
object YNfcFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(id = "ynfc", route = "ynfc", order = 400)
    override val startRoute = RouteId("ynfc")
    override val routes = setOf(startRoute)
}
