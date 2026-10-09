package com.yagay.ysuite.ui

import androidx.compose.runtime.Composable
import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId
import com.yagay.ysuite.productui.ProductSurfaceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class YSuiteFeatureRegistryTest {
    @Test
    fun registrySortsByOrderThenId() {
        val registry = YSuiteFeatureRegistry(
            listOf(
                FakeUiRegistration("beta", 20),
                FakeUiRegistration("alpha", 20),
                FakeUiRegistration("first", 10),
            ),
        )

        assertEquals(
            listOf("first", "alpha", "beta"),
            registry.features.map { it.contract.descriptor.id },
        )
    }

    @Test
    fun registryResolvesOwnedRoutes() {
        val registration = FakeUiRegistration("files", 10)
        val registry = YSuiteFeatureRegistry(
            listOf(registration),
        )

        assertEquals(
            registration,
            registry.findByRoute("files"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdsAreRejected() {
        YSuiteFeatureRegistry(
            listOf(
                FakeUiRegistration("same", 1),
                FakeUiRegistration("same", 2),
            ),
        )
    }
}

private class FakeUiRegistration(
    id: String,
    order: Int,
) : YSuiteFeatureUiRegistration {
    override val productSurface = ProductSurfaceKind.Tool

    override val contract = object : FeatureRegistration {
        override val descriptor = FeatureDescriptor(
            id = id,
            route = id,
            order = order,
        )
        override val startRoute = RouteId(id)
        override val routes = setOf(startRoute)
    }

    @Composable
    override fun label(): String = contract.descriptor.id

    @Composable
    override fun Content() = Unit
}
