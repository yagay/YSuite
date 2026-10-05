package com.yagay.ysuite.ui

import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.ShizukuGateway

data class YSuiteStandaloneDependencies(
    val rootGateway: RootGateway,
    val shizukuGateway: ShizukuGateway,
)

interface YSuiteStandaloneAwareRegistration {
    fun bindStandaloneDependencies(
        dependencies: YSuiteStandaloneDependencies,
    )
}
