package com.yagay.ysuite.navigation

import com.yagay.ysuite.model.FeatureDescriptor

@JvmInline
value class RouteId(val value: String) {
    init {
        require(value.isNotBlank()) { "RouteId cannot be blank" }
    }
}

data class NavigationEntry(
    val route: RouteId,
    val arguments: Map<String, String> = emptyMap(),
)

interface FeatureRegistration {
    val descriptor: FeatureDescriptor
    val startRoute: RouteId
    val routes: Set<RouteId>
}

interface FeatureRegistry {
    val features: List<FeatureRegistration>
}

interface Navigator {
    val current: NavigationEntry
    val backStack: List<NavigationEntry>

    fun navigate(entry: NavigationEntry)
    fun replace(entry: NavigationEntry)
    fun back(): Boolean
}

class BackStackNavigator(
    initial: NavigationEntry,
) : Navigator {
    private val stack = mutableListOf(initial)

    override val current: NavigationEntry
        get() = stack.last()

    override val backStack: List<NavigationEntry>
        get() = stack.toList()

    override fun navigate(entry: NavigationEntry) {
        if (entry != current) stack += entry
    }

    override fun replace(entry: NavigationEntry) {
        stack[stack.lastIndex] = entry
    }

    override fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}
