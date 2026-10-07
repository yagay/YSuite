package com.yagay.ysuite.feature.yfloat.api
import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId
object YFloatFeatureContract : FeatureRegistration {
    override val descriptor = FeatureDescriptor(id = "yfloat", route = "yfloat", order = 420)
    override val startRoute = RouteId("yfloat")
    override val routes = setOf(startRoute)
}
