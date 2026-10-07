package com.yagay.ysuite.productui

enum class UpstreamLicense {
    MIT,
    Apache2,
    ReferenceOnly,
}

data class UpstreamProductSpec(
    val repository: String,
    val license: UpstreamLicense,
    val role: String,
)

object UpstreamProductCatalog {
    val dashboard = UpstreamProductSpec(
        repository = "android/nowinandroid",
        license = UpstreamLicense.Apache2,
        role = "Adaptive app shell, dashboard composition and navigation patterns",
    )

    val fileManager = UpstreamProductSpec(
        repository = "SysAdminDoc/FileExplorer",
        license = UpstreamLicense.MIT,
        role = "File browser, drawer, breadcrumb, dual-pane and selection workflow",
    )

    val browser = UpstreamProductSpec(
        repository = "fazza-abiyyu/Yue-Browser",
        license = UpstreamLicense.Apache2,
        role = "Address bar, tabs, browser-owned toolbar and web workspace",
    )

    val settings = UpstreamProductSpec(
        repository = "alorma/Compose-Settings",
        license = UpstreamLicense.MIT,
        role = "Settings groups, switches, menu links and preference semantics",
    )

    val logViewer = UpstreamProductSpec(
        repository = "darshanparajuli/LogcatReader",
        license = UpstreamLicense.MIT,
        role = "Search, package/tag/level filtering and log stream workflow",
    )

    val downloadManager = UpstreamProductSpec(
        repository = "PBhadoo/QDM-Android",
        license = UpstreamLicense.Apache2,
        role = "Download tabs, task rows, progress and state-dependent actions",
    )

    val taskManager = UpstreamProductSpec(
        repository = "RohitKushvaha01/TaskManager",
        license = UpstreamLicense.Apache2,
        role = "Android resources/processes navigation, process search/filtering and focused process details",
    )

    val automation = UpstreamProductSpec(
        repository = "SysAdminDoc/OpenTasker",
        license = UpstreamLicense.MIT,
        role = "Profiles/tasks/actions authoring and editor workflow",
    )

    val entityManager = UpstreamProductSpec(
        repository = "LibChecker/LibChecker",
        license = UpstreamLicense.Apache2,
        role = "App/entity list, search/filtering, navigation rail and detail workflow",
    )

    val tool = dashboard
    val detail = dashboard
    val fullscreen = fileManager

    fun forKind(kind: ProductSurfaceKind): UpstreamProductSpec =
        when (kind) {
            ProductSurfaceKind.Dashboard -> dashboard
            ProductSurfaceKind.FileManager -> fileManager
            ProductSurfaceKind.Browser -> browser
            ProductSurfaceKind.Settings -> settings
            ProductSurfaceKind.LogViewer -> logViewer
            ProductSurfaceKind.DownloadManager -> downloadManager
            ProductSurfaceKind.TaskManager -> taskManager
            ProductSurfaceKind.AutomationStudio -> automation
            ProductSurfaceKind.EntityManager -> entityManager
            ProductSurfaceKind.Tool -> tool
            ProductSurfaceKind.Detail -> detail
            ProductSurfaceKind.Fullscreen -> fullscreen
        }
}
