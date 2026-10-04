package com.yagay.ysuite

import android.content.Context
import com.yagay.ysuite.diagnostics.DiagnosticCenter
import com.yagay.ysuite.diagnostics.DiagnosticCheck
import com.yagay.ysuite.diagnostics.DiagnosticFinding
import com.yagay.ysuite.diagnostics.DiagnosticStatus
import com.yagay.ysuite.feature.settings.SettingsFeatureUiRegistration
import com.yagay.ysuite.feature.system.SystemFeatureUiRegistration
import com.yagay.ysuite.feature.yfiles.YFilesEnvironmentFactory
import com.yagay.ysuite.feature.yfiles.YFilesFeatureUiRegistration
import com.yagay.ysuite.feature.ydownload.YDownloadEnvironmentFactory
import com.yagay.ysuite.feature.ydownload.YDownloadFeatureUiRegistration
import com.yagay.ysuite.logging.android.AndroidLogSink
import com.yagay.ysuite.logging.api.CompositeYSuiteLogger
import com.yagay.ysuite.logging.api.InMemoryLogStore
import com.yagay.ysuite.permissions.android.AndroidPermissionCatalog
import com.yagay.ysuite.permissions.android.AndroidPermissionChecker
import com.yagay.ysuite.platform.android.DefaultPlatformServices
import com.yagay.ysuite.platform.api.CapabilityKind
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.PlatformCapabilityMonitor
import com.yagay.ysuite.settings.DataStoreAppSettingsRepository
import com.yagay.ysuite.ui.YSuiteFeatureRegistry

class YSuiteAppContainer(
    context: Context,
) {
    val settings = DataStoreAppSettingsRepository(context)

    val logStore = InMemoryLogStore()
    val logger = CompositeYSuiteLogger(
        listOf(
            AndroidLogSink(),
            logStore,
        ),
    )

    val permissions = AndroidPermissionChecker(context)
    val permissionCatalog = AndroidPermissionCatalog(context)
    val platform = DefaultPlatformServices.create()
    val capabilityMonitor = PlatformCapabilityMonitor(platform)

    val diagnostics = DiagnosticCenter().apply {
        register(
            owner = "platform",
            checks = listOf(
                DiagnosticCheck {
                    val snapshot = capabilityMonitor.probe()
                    val status = snapshot[CapabilityKind.Root]
                    DiagnosticFinding(
                        id = "root",
                        status = status.toDiagnosticStatus(),
                        summary = status.name,
                    )
                },
                DiagnosticCheck {
                    val snapshot = capabilityMonitor.probe()
                    val status = snapshot[CapabilityKind.Hooks]
                    DiagnosticFinding(
                        id = "hooks",
                        status = status.toDiagnosticStatus(),
                        summary = status.name,
                    )
                },
            ),
        )
    }

    val featureRegistry = YSuiteFeatureRegistry(
        listOf(
            SystemFeatureUiRegistration(
                capabilityMonitor = capabilityMonitor,
                diagnosticCenter = diagnostics,
                logStore = logStore,
                logger = logger,
                permissionChecker = permissions,
                permissionCatalog = permissionCatalog,
            ),
            YFilesFeatureUiRegistration(
                environment = YFilesEnvironmentFactory.create(
                    context = context,
                    rootGateway = platform.root,
                ),
                logger = logger,
            ),
            YDownloadFeatureUiRegistration(
                environment = YDownloadEnvironmentFactory.create(
                    context = context,
                    logger = logger,
                ),
                logger = logger,
            ),
            SettingsFeatureUiRegistration(settings),
        ),
    )

    init {
        logger.debug(
            tag = "YSuite/App",
            message = "Composition root initialized",
        )
    }
}

private fun CapabilityStatus.toDiagnosticStatus(): DiagnosticStatus =
    when (this) {
        CapabilityStatus.Available -> DiagnosticStatus.Pass
        CapabilityStatus.PermissionRequired ->
            DiagnosticStatus.Warning
        CapabilityStatus.Unavailable ->
            DiagnosticStatus.Warning
        CapabilityStatus.Error ->
            DiagnosticStatus.Failure
    }
