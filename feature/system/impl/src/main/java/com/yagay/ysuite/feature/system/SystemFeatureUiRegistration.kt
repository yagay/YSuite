package com.yagay.ysuite.feature.system

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.diagnostics.DiagnosticCenter
import com.yagay.ysuite.feature.system.api.SystemFeatureContract
import com.yagay.ysuite.logging.api.LogStore
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.permissions.api.PermissionCatalog
import com.yagay.ysuite.permissions.api.PermissionChecker
import com.yagay.ysuite.platform.api.PlatformCapabilityMonitor
import com.yagay.ysuite.runtime.FeatureLifecycleEvent
import com.yagay.ysuite.runtime.FeatureLifecycleObserver
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class SystemFeatureUiRegistration(
    private val capabilityMonitor: PlatformCapabilityMonitor,
    private val diagnosticCenter: DiagnosticCenter,
    private val logStore: LogStore,
    private val logger: YSuiteLogger,
    private val permissionChecker: PermissionChecker,
    private val permissionCatalog: PermissionCatalog,
) : YSuiteFeatureUiRegistration {
    override val contract = SystemFeatureContract

    override val lifecycleObserver =
        FeatureLifecycleObserver { event ->
            when (event) {
                FeatureLifecycleEvent.Activated ->
                    logger.debug(TAG, "System feature activated")
                FeatureLifecycleEvent.Deactivated ->
                    logger.debug(TAG, "System feature deactivated")
            }
        }

    @Composable
    override fun label(): String =
        stringResource(R.string.system_title)

    @Composable
    override fun Content() {
        SystemFeatureScreen(
            capabilityMonitor = capabilityMonitor,
            diagnosticCenter = diagnosticCenter,
            logStore = logStore,
            logger = logger,
            permissionChecker = permissionChecker,
            permissionCatalog = permissionCatalog,
        )
    }

    companion object {
        private const val TAG = "YSuite/System"
    }
}
