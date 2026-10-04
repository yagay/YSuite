package com.yagay.ysuite

import android.content.Context
import com.yagay.ysuite.diagnostics.DiagnosticCenter
import com.yagay.ysuite.diagnostics.DiagnosticCheck
import com.yagay.ysuite.diagnostics.DiagnosticFinding
import com.yagay.ysuite.diagnostics.DiagnosticStatus
import com.yagay.ysuite.feature.settings.SettingsFeatureUiRegistration
import com.yagay.ysuite.feature.template.TemplateFeatureUiRegistration
import com.yagay.ysuite.logging.android.AndroidLogSink
import com.yagay.ysuite.logging.api.CompositeYSuiteLogger
import com.yagay.ysuite.logging.api.InMemoryLogStore
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
            TemplateFeatureUiRegistration,
            SettingsFeatureUiRegistration(settings),
        ),
    )
}

private fun CapabilityStatus.toDiagnosticStatus(): DiagnosticStatus =
    when (this) {
        CapabilityStatus.Available -> DiagnosticStatus.Pass
        CapabilityStatus.PermissionRequired -> DiagnosticStatus.Warning
        CapabilityStatus.Unavailable -> DiagnosticStatus.Warning
        CapabilityStatus.Error -> DiagnosticStatus.Failure
    }
