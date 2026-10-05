package com.yagay.ysuite.ui

internal fun topLevelDestinationStack(
    homeRoute: String,
    targetRoute: String?,
): List<String> =
    if (
        targetRoute.isNullOrBlank() ||
        targetRoute == homeRoute
    ) {
        listOf(homeRoute)
    } else {
        listOf(homeRoute, targetRoute)
    }
