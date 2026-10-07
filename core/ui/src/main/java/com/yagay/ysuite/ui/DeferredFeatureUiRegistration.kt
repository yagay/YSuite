package com.yagay.ysuite.ui

import androidx.compose.runtime.Composable
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.runtime.FeatureLifecycleObserver

/**
 * Keeps the home dashboard independent from feature initialization.
 *
 * Only immutable catalog metadata is available at startup. The feature implementation and
 * environment are constructed when the user actually activates that feature.
 */
class DeferredFeatureUiRegistration(
    override val contract: FeatureRegistration,
    override val productSurface: ProductSurfaceKind,
    private val label: String,
    factory: () -> YSuiteFeatureUiRegistration,
) : YSuiteFeatureUiRegistration {
    private val delegate =
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            factory().also { resolved ->
                require(
                    resolved.contract.descriptor.id ==
                        contract.descriptor.id,
                ) {
                    "Deferred feature id mismatch: " +
                        contract.descriptor.id +
                        " vs " +
                        resolved.contract.descriptor.id
                }
                require(
                    resolved.productSurface ==
                        productSurface,
                ) {
                    "Deferred product surface mismatch for " +
                        contract.descriptor.id
                }
            }
        }

    override val lifecycleObserver: FeatureLifecycleObserver
        get() = delegate.value.lifecycleObserver

    @Composable
    override fun label(): String = label

    @Composable
    override fun Content() {
        delegate.value.Content()
    }
}
