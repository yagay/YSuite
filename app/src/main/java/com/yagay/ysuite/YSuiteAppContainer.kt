package com.yagay.ysuite

import android.content.Context
import com.yagay.ysuite.diagnostics.DiagnosticCenter
import com.yagay.ysuite.diagnostics.DiagnosticCheck
import com.yagay.ysuite.diagnostics.DiagnosticFinding
import com.yagay.ysuite.diagnostics.DiagnosticStatus
import com.yagay.ysuite.feature.settings.SettingsFeatureUiRegistration
import com.yagay.ysuite.feature.settings.api.SettingsFeatureContract
import com.yagay.ysuite.feature.settings.R as SettingsR
import com.yagay.ysuite.feature.system.SystemFeatureUiRegistration
import com.yagay.ysuite.feature.system.api.SystemFeatureContract
import com.yagay.ysuite.feature.system.R as SystemR
import com.yagay.ysuite.feature.yfiles.YFilesEnvironmentFactory
import com.yagay.ysuite.feature.yfiles.YFilesFeatureUiRegistration
import com.yagay.ysuite.feature.yfiles.api.YFilesFeatureContract
import com.yagay.ysuite.feature.yfiles.R as YFilesR
import com.yagay.ysuite.feature.ydownload.YDownloadEnvironmentFactory
import com.yagay.ysuite.feature.ydownload.YDownloadFeatureUiRegistration
import com.yagay.ysuite.feature.ydownload.api.YDownloadFeatureContract
import com.yagay.ysuite.feature.ydownload.R as YDownloadR
import com.yagay.ysuite.feature.ytaskmanager.YTaskManagerEnvironmentFactory
import com.yagay.ysuite.feature.ytaskmanager.YTaskManagerFeatureUiRegistration
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskManagerFeatureContract
import com.yagay.ysuite.feature.ytaskmanager.R as YTaskManagerR
import com.yagay.ysuite.feature.yparam.YParamEnvironmentFactory
import com.yagay.ysuite.feature.yparam.YParamFeatureUiRegistration
import com.yagay.ysuite.feature.yparam.api.YParamFeatureContract
import com.yagay.ysuite.feature.yparam.R as YParamR
import com.yagay.ysuite.feature.ydiag.YDiagEnvironmentFactory
import com.yagay.ysuite.feature.ydiag.YDiagFeatureUiRegistration
import com.yagay.ysuite.feature.ydiag.api.YDiagFeatureContract
import com.yagay.ysuite.feature.ydiag.R as YDiagR
import com.yagay.ysuite.feature.ypower.YPowerEnvironmentFactory
import com.yagay.ysuite.feature.ypower.YPowerFeatureUiRegistration
import com.yagay.ysuite.feature.ypower.api.YPowerFeatureContract
import com.yagay.ysuite.feature.ypower.R as YPowerR
import com.yagay.ysuite.feature.ynotify.YNotifyFeatureUiRegistration
import com.yagay.ysuite.feature.ynotify.api.YNotifyFeatureContract
import com.yagay.ysuite.feature.ynotify.R as YNotifyR
import com.yagay.ysuite.feature.ynfc.YNfcEnvironmentFactory
import com.yagay.ysuite.feature.ynfc.YNfcFeatureUiRegistration
import com.yagay.ysuite.feature.ynfc.api.YNfcFeatureContract
import com.yagay.ysuite.feature.ynfc.R as YNfcR
import com.yagay.ysuite.feature.yminiguard.YMiniGuardEnvironmentFactory
import com.yagay.ysuite.feature.yminiguard.YMiniGuardFeatureUiRegistration
import com.yagay.ysuite.feature.yminiguard.api.YMiniGuardFeatureContract
import com.yagay.ysuite.feature.yminiguard.R as YMiniGuardR
import com.yagay.ysuite.feature.yentrycleaner.YEntryCleanerEnvironmentFactory
import com.yagay.ysuite.feature.yentrycleaner.YEntryCleanerFeatureUiRegistration
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryCleanerFeatureContract
import com.yagay.ysuite.feature.yentrycleaner.R as YEntryCleanerR
import com.yagay.ysuite.feature.yfloat.YFloatEnvironmentFactory
import com.yagay.ysuite.feature.yfloat.YFloatFeatureUiRegistration
import com.yagay.ysuite.feature.yfloat.api.YFloatFeatureContract
import com.yagay.ysuite.feature.yfloat.R as YFloatR
import com.yagay.ysuite.logging.android.AndroidLogSink
import com.yagay.ysuite.logging.android.AndroidLogcatCollector
import com.yagay.ysuite.logging.api.CompositeYSuiteLogger
import com.yagay.ysuite.logging.api.InMemoryLogStore
import com.yagay.ysuite.permissions.android.AndroidPermissionCatalog
import com.yagay.ysuite.permissions.android.AndroidPermissionChecker
import com.yagay.ysuite.permissions.api.PermissionStatus
import com.yagay.ysuite.platform.android.DefaultPlatformServices
import com.yagay.ysuite.platform.api.CapabilityKind
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.PlatformCapabilityMonitor
import com.yagay.ysuite.settings.DataStoreAppSettingsRepository
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.DeferredFeatureUiRegistration
import com.yagay.ysuite.ui.YSuiteFeatureRegistry

class YSuiteAppContainer(
    context: Context,
) {
    val settings =
        DataStoreAppSettingsRepository(context)

    val logStore = InMemoryLogStore()
    val logger =
        CompositeYSuiteLogger(
            listOf(
                AndroidLogSink(),
                logStore,
            ),
        )
    val logCollector =
        AndroidLogcatCollector()

    val permissions =
        AndroidPermissionChecker(context)
    val permissionCatalog =
        AndroidPermissionCatalog(context)

    val platform =
        DefaultPlatformServices.create()
    val capabilityMonitor =
        PlatformCapabilityMonitor(platform)

    val yFilesEnvironment by
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            YFilesEnvironmentFactory.create(
                context = context,
                rootGateway = platform.root,
                shizukuGateway =
                    platform.shizuku,
            )
        }

    val yDownloadEnvironment by
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            YDownloadEnvironmentFactory.create(
                context = context,
                logger = logger,
            )
        }

    val diagnostics =
        DiagnosticCenter().apply {
            replace(
                owner = "platform",
                checks =
                    CapabilityKind.entries.map {
                        kind ->
                        DiagnosticCheck {
                            val status =
                                capabilityMonitor
                                    .probe()[kind]
                            DiagnosticFinding(
                                id =
                                    "capability_" +
                                        kind.name
                                            .lowercase(),
                                status =
                                    status
                                        .toDiagnosticStatus(),
                                summary =
                                    status.name,
                                category =
                                    "capability",
                                recommendation =
                                    capabilityRecommendationKey(
                                        kind,
                                        status,
                                    ),
                                metadata =
                                    mapOf(
                                        "kind" to kind.name,
                                        "status" to status.name,
                                    ),
                            )
                        }
                    },
            )

            replace(
                owner = "permissions",
                checks =
                    listOf(
                        DiagnosticCheck {
                            val requirements =
                                permissionCatalog
                                    .requirements()
                            val result =
                                permissions.snapshot(
                                    requirements,
                                )
                            val denied =
                                result.statuses
                                    .count {
                                        it.value ==
                                            PermissionStatus.Denied
                                    }
                            DiagnosticFinding(
                                id =
                                    "declared_permissions",
                                status =
                                    if (denied == 0) {
                                        DiagnosticStatus.Pass
                                    } else {
                                        DiagnosticStatus.Warning
                                    },
                                summary =
                                    requirements.size
                                        .toString() +
                                        " declared · " +
                                        denied +
                                        " denied",
                                category =
                                    "permissions",
                                recommendation =
                                    if (denied > 0) {
                                        "review_permissions"
                                    } else {
                                        null
                                    },
                                metadata =
                                    mapOf(
                                        "declaredCount" to
                                            requirements.size.toString(),
                                        "deniedCount" to
                                            denied.toString(),
                                    ),
                            )
                        },
                    ),
            )

            replace(
                owner = "yfiles",
                checks =
                    listOf(
                        DiagnosticCheck {
                            val descriptors =
                                yFilesEnvironment
                                    .providerCatalog
                                    .descriptors
                            DiagnosticFinding(
                                id =
                                    "yfiles_providers",
                                status =
                                    DiagnosticStatus.Pass,
                                summary =
                                    descriptors.size
                                        .toString() +
                                        " providers",
                                details =
                                    descriptors
                                        .joinToString("\n") {
                                            descriptor ->
                                            descriptor.id +
                                                " · " +
                                                descriptor.kind.name +
                                                " · " +
                                                descriptor.accessMode.name +
                                                " · " +
                                                descriptor.capabilities.size +
                                                " capabilities"
                                        },
                                category =
                                    "filesystem",
                                metadata =
                                    mapOf(
                                        "providerCount" to
                                            descriptors.size
                                                .toString(),
                                    ),
                            )
                        },
                    ),
            )
        }

    val featureRegistry =
        YSuiteFeatureRegistry(
            listOf(
                DeferredFeatureUiRegistration(
                    contract = SystemFeatureContract,
                    productSurface = ProductSurfaceKind.Dashboard,
                    label = context.getString(SystemR.string.system_title),
                ) {
                    SystemFeatureUiRegistration(
                        capabilityMonitor = capabilityMonitor,
                        platformServices = platform,
                        diagnosticCenter = diagnostics,
                        logStore = logStore,
                        logCollector = logCollector,
                        logger = logger,
                        permissionChecker = permissions,
                        permissionCatalog = permissionCatalog,
                    )
                },
                DeferredFeatureUiRegistration(
                    contract = YFilesFeatureContract,
                    productSurface = ProductSurfaceKind.FileManager,
                    label = context.getString(YFilesR.string.yfiles_title),
                ) {
                    YFilesFeatureUiRegistration.lazy(
                        logger = logger,
                        environmentProvider = { yFilesEnvironment },
                    )
                },
                DeferredFeatureUiRegistration(
                    contract = YDownloadFeatureContract,
                    productSurface = ProductSurfaceKind.DownloadManager,
                    label = context.getString(YDownloadR.string.ydownload_title),
                ) {
                    YDownloadFeatureUiRegistration.lazy(
                        logger = logger,
                        environmentProvider = { yDownloadEnvironment },
                    )
                },
                DeferredFeatureUiRegistration(
                    contract = YTaskManagerFeatureContract,
                    productSurface = ProductSurfaceKind.TaskManager,
                    label = context.getString(YTaskManagerR.string.ytask_title),
                ) {
                    YTaskManagerFeatureUiRegistration(
                        environment =
                            YTaskManagerEnvironmentFactory.create(
                                context = context,
                                rootGateway = platform.root,
                                hookGateway = platform.hooks,
                                logger = logger,
                            ),
                    )
                },
                DeferredFeatureUiRegistration(
                    contract = YParamFeatureContract,
                    productSurface = ProductSurfaceKind.EntityManager,
                    label = context.getString(YParamR.string.yparam_title),
                ) {
                    YParamFeatureUiRegistration(
                        environment =
                            YParamEnvironmentFactory.create(
                                context = context,
                                hookGateway = platform.hooks,
                                logger = logger,
                            ),
                    )
                },
                DeferredFeatureUiRegistration(
                    contract = YDiagFeatureContract,
                    productSurface = ProductSurfaceKind.LogViewer,
                    label = context.getString(YDiagR.string.ydiag_title),
                ) {
                    YDiagFeatureUiRegistration(
                        environment =
                            YDiagEnvironmentFactory.create(
                                context = context,
                                rootGateway = platform.root,
                                hookGateway = platform.hooks,
                                logger = logger,
                            ),
                    )
                },
                DeferredFeatureUiRegistration(
                    contract = YPowerFeatureContract,
                    productSurface = ProductSurfaceKind.EntityManager,
                    label = context.getString(YPowerR.string.ypower_title),
                ) {
                    YPowerFeatureUiRegistration(
                        environment =
                            YPowerEnvironmentFactory.create(
                                context = context,
                                rootGateway = platform.root,
                                hookGateway = platform.hooks,
                                logger = logger,
                            ),
                    )
                },
                DeferredFeatureUiRegistration(
                    contract = YNotifyFeatureContract,
                    productSurface = ProductSurfaceKind.LogViewer,
                    label = context.getString(YNotifyR.string.ynotify_title),
                ) {
                    YNotifyFeatureUiRegistration
                },
                DeferredFeatureUiRegistration(
                    contract = YNfcFeatureContract,
                    productSurface = ProductSurfaceKind.Tool,
                    label = context.getString(YNfcR.string.ynfc_title),
                ) {
                    YNfcFeatureUiRegistration(
                        environment =
                            YNfcEnvironmentFactory.create(
                                context = context,
                                rootGateway = platform.root,
                                hookGateway = platform.hooks,
                                logger = logger,
                            ),
                    )
                },
                DeferredFeatureUiRegistration(
                    contract = YFloatFeatureContract,
                    productSurface = ProductSurfaceKind.Settings,
                    label = context.getString(YFloatR.string.yfloat_title),
                ) {
                    YFloatFeatureUiRegistration(
                        environment =
                            YFloatEnvironmentFactory.create(
                                context = context,
                                rootGateway = platform.root,
                                hookGateway = platform.hooks,
                                logger = logger,
                            ),
                    )
                },
                DeferredFeatureUiRegistration(
                    contract = YMiniGuardFeatureContract,
                    productSurface = ProductSurfaceKind.EntityManager,
                    label = context.getString(YMiniGuardR.string.yminiguard_title),
                ) {
                    YMiniGuardFeatureUiRegistration(
                        environment =
                            YMiniGuardEnvironmentFactory.create(
                                context = context,
                                rootGateway = platform.root,
                                hookGateway = platform.hooks,
                                logger = logger,
                            ),
                    )
                },
                DeferredFeatureUiRegistration(
                    contract = YEntryCleanerFeatureContract,
                    productSurface = ProductSurfaceKind.EntityManager,
                    label = context.getString(YEntryCleanerR.string.yentry_title),
                ) {
                    YEntryCleanerFeatureUiRegistration(
                        environment =
                            YEntryCleanerEnvironmentFactory.create(
                                context = context,
                                rootGateway = platform.root,
                                hookGateway = platform.hooks,
                                logger = logger,
                            ),
                    )
                },
                DeferredFeatureUiRegistration(
                    contract = SettingsFeatureContract,
                    productSurface = ProductSurfaceKind.Settings,
                    label = context.getString(SettingsR.string.settings_title),
                ) {
                    SettingsFeatureUiRegistration(settings)
                },
            ),
        )

    init {
        logger.debug(
            tag = "YSuite/App",
            message =
                "Composition root initialized",
        )
    }
}

private fun CapabilityStatus.toDiagnosticStatus():
    DiagnosticStatus =
    when (this) {
        CapabilityStatus.Available ->
            DiagnosticStatus.Pass
        CapabilityStatus.PermissionRequired ->
            DiagnosticStatus.Warning
        CapabilityStatus.Unavailable ->
            DiagnosticStatus.Warning
        CapabilityStatus.Error ->
            DiagnosticStatus.Failure
    }

private fun capabilityRecommendationKey(
    kind: CapabilityKind,
    status: CapabilityStatus,
): String? =
    when {
        status ==
            CapabilityStatus.Available ->
            null
        kind ==
            CapabilityKind.Shizuku &&
            status ==
            CapabilityStatus.PermissionRequired ->
            "grant_shizuku"
        kind ==
            CapabilityKind.Shizuku ->
            "start_shizuku"
        kind ==
            CapabilityKind.Root ->
            "grant_root"
        kind ==
            CapabilityKind.Hooks ->
            "enable_hooks"
        else -> null
    }
