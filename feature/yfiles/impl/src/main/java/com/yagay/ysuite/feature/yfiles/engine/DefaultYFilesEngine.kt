package com.yagay.ysuite.feature.yfiles.engine

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileProvider
import com.yagay.ysuite.feature.yfiles.api.YFileProviderDescriptor
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFilesEngine

class DefaultYFilesEngine(
    private val registry: YFileProviderRegistry,
) : YFilesEngine {
    override val providers:
        List<YFileProviderDescriptor>
        get() = registry.descriptors

    override fun root(
        providerId: String,
    ): Outcome<YFileRef> =
        withProvider(providerId) { provider ->
            Outcome.Success(provider.root())
        }

    override fun parent(
        ref: YFileRef,
    ): Outcome<YFileRef?> =
        withProvider(ref) { provider ->
            Outcome.Success(provider.parent(ref))
        }

    override suspend fun list(
        directory: YFileRef,
        query: YFileQuery,
    ): Outcome<List<YFileNode>> =
        withProviderSuspend(directory) { provider ->
            provider.list(directory, query)
        }

    override suspend fun stat(
        ref: YFileRef,
    ): Outcome<YFileNode> =
        withProviderSuspend(ref) { provider ->
            provider.stat(ref)
        }

    override suspend fun createDirectory(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        withProviderSuspend(parent) { provider ->
            provider.createDirectory(parent, name)
        }

    override suspend fun createFile(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        withProviderSuspend(parent) { provider ->
            provider.createFile(parent, name)
        }

    override suspend fun rename(
        ref: YFileRef,
        newName: String,
    ): Outcome<YFileNode> =
        withProviderSuspend(ref) { provider ->
            provider.rename(ref, newName)
        }

    override suspend fun delete(
        ref: YFileRef,
    ): Outcome<Unit> =
        withProviderSuspend(ref) { provider ->
            provider.delete(ref)
        }

    override suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
    ): Outcome<YFileNode> =
        sameProvider(
            source,
            destinationDirectory,
        ) { provider ->
            provider.copy(
                source,
                destinationDirectory,
            )
        }

    override suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
    ): Outcome<YFileNode> =
        sameProvider(
            source,
            destinationDirectory,
        ) { provider ->
            provider.move(
                source,
                destinationDirectory,
            )
        }

    private inline fun <T> withProvider(
        providerId: String,
        block: (YFileProvider) -> Outcome<T>,
    ): Outcome<T> =
        when (
            val provider = registry.provider(providerId)
        ) {
            is Outcome.Success ->
                block(provider.value)
            is Outcome.Failure ->
                provider
        }

    private inline fun <T> withProvider(
        ref: YFileRef,
        block: (YFileProvider) -> Outcome<T>,
    ): Outcome<T> =
        when (val provider = registry.provider(ref)) {
            is Outcome.Success ->
                block(provider.value)
            is Outcome.Failure ->
                provider
        }

    private suspend inline fun <T> withProviderSuspend(
        ref: YFileRef,
        crossinline block:
            suspend (YFileProvider) -> Outcome<T>,
    ): Outcome<T> =
        when (val provider = registry.provider(ref)) {
            is Outcome.Success ->
                block(provider.value)
            is Outcome.Failure ->
                provider
        }

    private suspend inline fun <T> sameProvider(
        source: YFileRef,
        destination: YFileRef,
        crossinline block:
            suspend (YFileProvider) -> Outcome<T>,
    ): Outcome<T> {
        if (source.providerId != destination.providerId) {
            return Outcome.Failure(
                code = "cross_provider_transfer_pending",
                message = CROSS_PROVIDER_PENDING_MESSAGE,
            )
        }
        return withProviderSuspend(source, block)
    }

    companion object {
        private const val CROSS_PROVIDER_PENDING_MESSAGE =
            "Cross-provider transfer is not enabled yet"
    }
}
