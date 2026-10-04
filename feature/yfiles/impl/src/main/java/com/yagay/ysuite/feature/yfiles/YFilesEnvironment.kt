package com.yagay.ysuite.feature.yfiles

import android.content.Context
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.engine.DefaultYFilesEngine
import com.yagay.ysuite.feature.yfiles.engine.YFileProviderRegistry
import com.yagay.ysuite.feature.yfiles.provider.archive.YFilesArchiveController
import com.yagay.ysuite.feature.yfiles.provider.archive.ZipArchiveProvider
import com.yagay.ysuite.feature.yfiles.provider.document.DocumentFileProvider
import com.yagay.ysuite.feature.yfiles.provider.document.DocumentTreeStore
import com.yagay.ysuite.feature.yfiles.provider.local.LocalFileProvider
import com.yagay.ysuite.feature.yfiles.provider.root.RootFileProvider
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import com.yagay.ysuite.platform.api.RootResult

data class YFilesEnvironment(
    val engine: DefaultYFilesEngine,
    val documentTrees: DocumentTreeStore,
    val archives: YFilesArchiveController,
    val places: YFilesPlacesStore,
    val trash: YFilesTrashService,
    val tools: YFilesToolsService,
)

object YFilesEnvironmentFactory {
    fun create(
        context: Context,
        rootGateway: RootGateway,
    ): YFilesEnvironment {
        val appContext =
            context.applicationContext
        val documentTrees =
            DocumentTreeStore(appContext)
        val archiveProvider =
            ZipArchiveProvider()
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
                        java.io.File(
                            appContext.cacheDir,
                            "yfiles-tools",
                        ),
                ),
        )
    }

    fun createWithoutRoot(
        context: Context,
    ): YFilesEnvironment =
        create(
            context,
            UnavailableRootGateway,
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
