package com.yagay.ysuite.feature.yfiles.engine

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileProvider
import com.yagay.ysuite.feature.yfiles.api.YFileProviderCapabilityMatrix
import com.yagay.ysuite.feature.yfiles.api.YFileProviderCatalog
import com.yagay.ysuite.feature.yfiles.api.YFileProviderDescriptor
import com.yagay.ysuite.feature.yfiles.api.YFileRef

class YFileProviderRegistry(
    providers: List<YFileProvider>,
) : YFileProviderCatalog {
    private val providersById: Map<String, YFileProvider>

    override val descriptors:
        List<YFileProviderDescriptor>

    init {
        require(providers.isNotEmpty()) {
            "At least one file provider is required"
        }
        val duplicates =
            providers
                .groupBy { it.descriptor.id }
                .filterValues { it.size > 1 }
                .keys
        require(duplicates.isEmpty()) {
            "Duplicate file provider ids: " +
                duplicates.sorted()
                    .joinToString()
        }

        providersById =
            providers.associateBy {
                it.descriptor.id
            }
        descriptors =
            providers
                .map(YFileProvider::descriptor)
                .sortedBy(
                    YFileProviderDescriptor::id,
                )
    }

    fun provider(
        providerId: String,
    ): Outcome<YFileProvider> {
        val provider = providersById[providerId]
        return if (provider != null) {
            Outcome.Success(provider)
        } else {
            Outcome.Failure(
                code = "provider_not_found",
                message =
                    PROVIDER_NOT_FOUND_MESSAGE,
            )
        }
    }

    fun provider(
        ref: YFileRef,
    ): Outcome<YFileProvider> =
        provider(ref.providerId)

    override fun descriptor(
        providerId: String,
    ): Outcome<YFileProviderDescriptor> =
        when (
            val provider =
                provider(providerId)
        ) {
            is Outcome.Success ->
                Outcome.Success(
                    provider.value.descriptor,
                )
            is Outcome.Failure ->
                provider
        }

    override fun capabilityMatrix(
        providerId: String,
    ): Outcome<YFileProviderCapabilityMatrix> =
        when (
            val descriptor =
                descriptor(providerId)
        ) {
            is Outcome.Success ->
                Outcome.Success(
                    descriptor.value.let {
                        YFileProviderCapabilityMatrix(
                            providerId = it.id,
                            kind = it.kind,
                            accessMode =
                                it.accessMode,
                            readOnly =
                                it.readOnly,
                            supported =
                                it.capabilities,
                        )
                    },
                )
            is Outcome.Failure ->
                descriptor
        }

    companion object {
        private const val PROVIDER_NOT_FOUND_MESSAGE =
            "Unknown file provider"
    }
}
