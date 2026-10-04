package com.yagay.ysuite.ui

import com.yagay.ysuite.platform.api.RootGateway

data class YSuiteStandaloneDependencies(
    val rootGateway: RootGateway,
)

interface YSuiteStandaloneAwareRegistration {
    fun bindStandaloneDependencies(
        dependencies: YSuiteStandaloneDependencies,
    )
}
