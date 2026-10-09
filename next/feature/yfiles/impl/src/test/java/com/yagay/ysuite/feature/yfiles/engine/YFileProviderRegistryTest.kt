package com.yagay.ysuite.feature.yfiles.engine

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileCapability
import com.yagay.ysuite.feature.yfiles.api.YFileProviderAccessMode
import com.yagay.ysuite.feature.yfiles.provider.local.LocalFileProvider
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YFileProviderRegistryTest {
    @Test
    fun registryRejectsDuplicateProviderIds() {
        val root =
            Files.createTempDirectory("yfiles-registry")
                .toFile()
        try {
            assertThrows(
                IllegalArgumentException::class.java,
            ) {
                YFileProviderRegistry(
                    listOf(
                        LocalFileProvider(
                            root.absolutePath,
                        ),
                        LocalFileProvider(
                            root.absolutePath,
                        ),
                    ),
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun registryExposesRegisteredDescriptor() {
        val root =
            Files.createTempDirectory("yfiles-registry")
                .toFile()
        try {
            val registry = YFileProviderRegistry(
                listOf(
                    LocalFileProvider(
                        root.absolutePath,
                    ),
                ),
            )

            assertEquals(
                listOf("local"),
                registry.descriptors.map { it.id },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun registryExposesCapabilityMatrix() {
        val root =
            Files.createTempDirectory(
                "yfiles-registry",
            ).toFile()
        try {
            val registry =
                YFileProviderRegistry(
                    listOf(
                        LocalFileProvider(
                            root.absolutePath,
                        ),
                    ),
                )

            val matrix =
                (
                    registry
                        .capabilityMatrix("local")
                        as Outcome.Success
                ).value

            assertEquals(
                YFileProviderAccessMode.Direct,
                matrix.accessMode,
            )
            assertEquals(
                true,
                matrix.supports(
                    YFileCapability.Write,
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
