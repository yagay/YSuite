package com.yagay.ysuite.feature.yfiles

import android.content.Context
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.engine.DefaultYFilesEngine
import com.yagay.ysuite.feature.yfiles.engine.YFileProviderRegistry
import com.yagay.ysuite.feature.yfiles.provider.archive.YFilesArchiveController
import com.yagay.ysuite.feature.yfiles.provider.archive.UniversalArchiveProvider
import com.yagay.ysuite.feature.yfiles.provider.document.DocumentFileProvider
import com.yagay.ysuite.feature.yfiles.provider.document.DocumentTreeStore
import com.yagay.ysuite.feature.yfiles.provider.local.LocalFileProvider
import com.yagay.ysuite.feature.yfiles.provider.root.RootFileProvider
import com.yagay.ysuite.feature.yfiles.provider.remote.RemoteFileProvider
import com.yagay.ysuite.feature.yfiles.provider.remote.YFilesNetworkStore
import com.yagay.ysuite.feature.yfiles.provider.collection.MediaCollectionProvider
import com.yagay.ysuite.feature.yfiles.provider.shizuku.ShizukuFileProvider
import com.yagay.ysuite.feature.yfiles.provider.cloud.CloudFileProvider
import com.yagay.ysuite.feature.yfiles.provider.cloud.YFilesCloudStore
import com.yagay.ysuite.feature.yfiles.plugin.YFilesPluginRegistry
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import com.yagay.ysuite.platform.api.RootResult
import com.yagay.ysuite.platform.api.ShizukuGateway

data class YFilesEnvironment(
    val engine: DefaultYFilesEngine,
    val documentTrees: DocumentTreeStore,
    val archives: YFilesArchiveController,
    val places: YFilesPlacesStore,
    val trash: YFilesTrashService,
    val tools: YFilesToolsService,
    val networkStore: YFilesNetworkStore,
    val remoteProvider: RemoteFileProvider,
    val workspace: YFilesWorkspaceStore,
    val transfers: YFilesTransferQueue,
    val apps: YFilesAppsService,
    val security: YFilesSecurityService,
    val preview: YFilesPreviewService,
    val rootTools: YFilesRootToolsService,
    val shizukuGateway: ShizukuGateway,
    val cloudStore: YFilesCloudStore,
    val cloudProvider: CloudFileProvider,
    val plugins: YFilesPluginRegistry,
    val shareServerStore: YFilesShareServerStore,
    val automationSettings: YFilesAutomationSettings,
    val rootGateway: RootGateway,
)

object YFilesEnvironmentFactory {
    fun create(
        context: Context,
        rootGateway: RootGateway,
        shizukuGateway: ShizukuGateway =
            UnavailableShizukuGateway,
    ): YFilesEnvironment {
        val appContext =
            context.applicationContext
        val documentTrees =
            DocumentTreeStore(appContext)
        val archiveProvider =
            UniversalArchiveProvider(
                cacheDirectory =
                    java.io.File(
                        appContext.cacheDir,
                        "yfiles-archive-mounts",
                    ),
                readOnlyMessage =
                    appContext.getString(
                        R.string.yfiles_msg_archive_read_only,
                    ),
            )
        val networkStore =
            YFilesNetworkStore(appContext)
        val cloudStore =
            YFilesCloudStore(appContext)
        val cloudProvider =
            CloudFileProvider(
                store = cloudStore,
                client =
                    okhttp3.OkHttpClient
                        .Builder()
                        .retryOnConnectionFailure(
                            true,
                        )
                        .build(),
                cacheDirectory =
                    java.io.File(
                        appContext.cacheDir,
                        "yfiles-cloud",
                    ),
            )
        val remoteProvider =
            RemoteFileProvider(
                appContext,
                networkStore,
            )
        val collectionProvider =
            MediaCollectionProvider(
                appContext,
                appContext.contentResolver,
            )
        val shizukuProvider =
            ShizukuFileProvider(
                shizukuGateway,
            )
        val engine = DefaultYFilesEngine(
            YFileProviderRegistry(
                listOf(
                    LocalFileProvider(),
                    DocumentFileProvider(
                        resolver =
                            appContext.contentResolver,
                        store = documentTrees,
                    ),
                    RootFileProvider(
                        rootGateway,
                    ),
                    archiveProvider,
                    remoteProvider,
                    collectionProvider,
                    shizukuProvider,
                    cloudProvider,
                ),
            ),
        )
        val archives =
            YFilesArchiveController(
                engine = engine,
                provider = archiveProvider,
                cacheDirectory = java.io.File(
                    appContext.cacheDir,
                    "yfiles-archives",
                ),
            )

        val workspace =
            YFilesWorkspaceStore(appContext)
        val transfers =
            YFilesTransferQueue(
                appContext,
                engine,
            )
        val toolsCache =
            java.io.File(
                appContext.cacheDir,
                "yfiles-tools",
            )
        val apps =
            YFilesAppsService(
                appContext,
                engine,
                java.io.File(
                    appContext.cacheDir,
                    "yfiles-apk",
                ),
            )
        val security =
            YFilesSecurityService(
                appContext,
                engine,
                java.io.File(
                    appContext.cacheDir,
                    "yfiles-security",
                ),
            )
        val preview =
            YFilesPreviewService(
                engine,
                java.io.File(
                    appContext.cacheDir,
                    "yfiles-preview",
                ),
            )

        return YFilesEnvironment(
            engine = engine,
            documentTrees = documentTrees,
            archives = archives,
            places =
                YFilesPlacesStore(appContext),
            trash =
                YFilesTrashService(
                    appContext,
                    engine,
                ),
            tools =
                YFilesToolsService(
                    engine = engine,
                    archives = archives,
                    cacheDirectory =
                        toolsCache,
                ),
            networkStore = networkStore,
            remoteProvider = remoteProvider,
            workspace = workspace,
            transfers = transfers,
            apps = apps,
            security = security,
            preview = preview,
            rootTools =
                YFilesRootToolsService(
                    appContext,
                    rootGateway,
                ),
            shizukuGateway =
                shizukuGateway,
            cloudStore = cloudStore,
            cloudProvider = cloudProvider,
            plugins =
                YFilesPluginRegistry(
                    appContext,
                ),
            shareServerStore =
                YFilesShareServerStore(
                    appContext,
                ),
            automationSettings =
                YFilesAutomationSettings(
                    appContext,
                ),
            rootGateway = rootGateway,
        )
    }

    fun createWithoutRoot(
        context: Context,
    ): YFilesEnvironment =
        create(
            context = context,
            rootGateway =
                UnavailableRootGateway,
            shizukuGateway =
                UnavailableShizukuGateway,
        )
}

private object UnavailableRootGateway :
    RootGateway {
    override suspend fun status():
        CapabilityStatus =
        CapabilityStatus.Unavailable

    override suspend fun execute(
        request: RootRequest,
    ): Outcome<RootResult> =
        Outcome.Failure(
            code = "root_unavailable",
            message = ROOT_UNAVAILABLE_MESSAGE,
        )

    private const val ROOT_UNAVAILABLE_MESSAGE =
        "Root is unavailable in this host"
}


private object UnavailableShizukuGateway :
    ShizukuGateway {
    override suspend fun status():
        CapabilityStatus =
        CapabilityStatus.Unavailable

    override fun requestPermission(
        requestCode: Int,
    ): Boolean = false

    override suspend fun execute(
        request: RootRequest,
    ): Outcome<RootResult> =
        Outcome.Failure(
            code = "shizuku_unavailable",
            message =
                "Shizuku is unavailable in this host",
        )
}
