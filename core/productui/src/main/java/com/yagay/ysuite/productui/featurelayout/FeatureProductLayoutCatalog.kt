package com.yagay.ysuite.productui.featurelayout

import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.productui.UpstreamLicense

data class FeatureProductLayoutReference(
    val repository: String,
    val license: UpstreamLicense,
)

data class FeatureProductLayoutSpec(
    val featureId: String,
    val surfaceKind: ProductSurfaceKind,
    val workspaceSymbol: String,
    val primaryReference: FeatureProductLayoutReference,
    val additionalReferences: List<FeatureProductLayoutReference> = emptyList(),
)

object FeatureProductLayoutCatalog {
    val specs = listOf(
        FeatureProductLayoutSpec("yentrycleaner", ProductSurfaceKind.EntityManager, "YEntryCleanerWorkspace", FeatureProductLayoutReference("LibChecker/LibChecker", UpstreamLicense.Apache2), listOf(FeatureProductLayoutReference("MuntashirAkon/AppManager", UpstreamLicense.ReferenceOnly))),
        FeatureProductLayoutSpec("ydiag", ProductSurfaceKind.LogViewer, "YDiagWorkspace", FeatureProductLayoutReference("darshanparajuli/LogcatReader", UpstreamLicense.MIT), listOf(FeatureProductLayoutReference("F0x1d/LogFox", UpstreamLicense.ReferenceOnly))),
        FeatureProductLayoutSpec("ynotify", ProductSurfaceKind.LogViewer, "YNotifyWorkspace", FeatureProductLayoutReference("darshanparajuli/LogcatReader", UpstreamLicense.MIT)),
        FeatureProductLayoutSpec("ypower", ProductSurfaceKind.EntityManager, "YPowerWorkspace", FeatureProductLayoutReference("LibChecker/LibChecker", UpstreamLicense.Apache2), listOf(FeatureProductLayoutReference("luckyzyx/LuckyTool", UpstreamLicense.ReferenceOnly))),
        FeatureProductLayoutSpec("yminiguard", ProductSurfaceKind.EntityManager, "YMiniGuardWorkspace", FeatureProductLayoutReference("LibChecker/LibChecker", UpstreamLicense.Apache2), listOf(FeatureProductLayoutReference("luckyzyx/LuckyTool", UpstreamLicense.ReferenceOnly))),
        FeatureProductLayoutSpec("ynfc", ProductSurfaceKind.Tool, "YNfcWorkspace", FeatureProductLayoutReference("nfcgate/nfcgate", UpstreamLicense.Apache2)),
        FeatureProductLayoutSpec("ytaskmanager", ProductSurfaceKind.TaskManager, "YTaskManagerWorkspace", FeatureProductLayoutReference("RohitKushvaha01/TaskManager", UpstreamLicense.Apache2)),
        FeatureProductLayoutSpec("yparam", ProductSurfaceKind.EntityManager, "YParamWorkspace", FeatureProductLayoutReference("LibChecker/LibChecker", UpstreamLicense.Apache2), listOf(FeatureProductLayoutReference("Kwensiu/DPIS", UpstreamLicense.ReferenceOnly), FeatureProductLayoutReference("rovo89/XposedAppSettings", UpstreamLicense.ReferenceOnly))),
        FeatureProductLayoutSpec("yfloat", ProductSurfaceKind.Settings, "YFloatWorkspace", FeatureProductLayoutReference("alorma/Compose-Settings", UpstreamLicense.MIT), listOf(FeatureProductLayoutReference("rhengtl/textsnip", UpstreamLicense.MIT))),
    )

    fun forFeature(featureId: String): FeatureProductLayoutSpec? =
        specs.firstOrNull { it.featureId == featureId }
}
