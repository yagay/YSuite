package com.yagay.ysuite.feature.ytaskmanager

import android.content.Context
import android.content.pm.PackageManager
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskGpuSnapshot

internal class YTaskGpuSampler(
    context: Context,
    private val shell: YTaskRootRunner,
) {
    private val packageManager =
        context.packageManager

    suspend fun read():
        YTaskGpuSnapshot {
        val raw =
            shell.text(
                """
                busy=""
                if [ -r /sys/class/kgsl/kgsl-3d0/gpubusy ]; then
                  busy=$(cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null)
                else
                  for f in /sys/class/devfreq/*gpu*/load /sys/class/devfreq/*mali*/load; do
                    [ -r "@f" ] && { busy=$(cat "@f" 2>/dev/null); break; }
                  done
                fi
                echo "__BUSY__|@busy"
                cur=""; min=""; max=""
                for f in /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq /sys/class/devfreq/*gpu*/cur_freq /sys/class/devfreq/*mali*/cur_freq; do
                  [ -r "@f" ] && { cur=$(cat "@f" 2>/dev/null); break; }
                done
                for f in /sys/class/kgsl/kgsl-3d0/devfreq/min_freq /sys/class/devfreq/*gpu*/min_freq /sys/class/devfreq/*mali*/min_freq; do
                  [ -r "@f" ] && { min=$(cat "@f" 2>/dev/null); break; }
                done
                for f in /sys/class/kgsl/kgsl-3d0/devfreq/max_freq /sys/class/devfreq/*gpu*/max_freq /sys/class/devfreq/*mali*/max_freq; do
                  [ -r "@f" ] && { max=$(cat "@f" 2>/dev/null); break; }
                done
                echo "__FREQ__|@cur|@min|@max"
                dumpsys SurfaceFlinger 2>/dev/null | grep -m1 -E 'GLES:|GL_RENDERER|GPU' || true
                """.trimIndent().replace('@', '
            )
        val busy =
            raw.lineSequence()
                .firstOrNull {
                    it.startsWith(
                        "__BUSY__|",
                    )
                }
                ?.substringAfter('|')
                .orEmpty()
        val freq =
            raw.lineSequence()
                .firstOrNull {
                    it.startsWith(
                        "__FREQ__|",
                    )
                }
                ?.split('|')
                .orEmpty()
        val numbers =
            Regex("\d+")
                .findAll(busy)
                .mapNotNull {
                    it.value
                        .toLongOrNull()
                }
                .toList()
        val usage =
            when {
                numbers.size >= 2 &&
                    numbers[1] > 0L ->
                    (
                        numbers[0] *
                            100f /
                            numbers[1]
                        ).coerceIn(
                        0f,
                        100f,
                    )
                numbers.isNotEmpty() &&
                    numbers[0] <= 100L ->
                    numbers[0]
                        .toFloat()
                else -> null
            }
        val glLine =
            raw.lineSequence()
                .firstOrNull {
                    !it.startsWith("__") &&
                        (
                            it.contains(
                                "GLES:",
                                true,
                            ) ||
                                it.contains(
                                    "GL_RENDERER",
                                    true,
                                )
                            )
                }
                .orEmpty()
        val parts =
            glLine.substringAfter(
                "GLES:",
                glLine,
            ).split(',')
                .map {
                    it.trim()
                }

        val vulkan =
            packageManager.hasSystemFeature(
                PackageManager
                    .FEATURE_VULKAN_HARDWARE_LEVEL,
            ) ||
                packageManager.hasSystemFeature(
                    PackageManager
                        .FEATURE_VULKAN_HARDWARE_VERSION,
                )
        val api =
            packageManager
                .systemAvailableFeatures
                .firstOrNull {
                    it.name ==
                        PackageManager
                            .FEATURE_VULKAN_HARDWARE_VERSION
                }?.version
                ?.let {
                    value ->
                    val major =
                        value ushr 22
                    val minor =
                        (
                            value ushr 12
                            ) and 0x3ff
                    val patch =
                        value and 0xfff
                    "$major.$minor.$patch"
                }

        return YTaskGpuSnapshot(
            vendor =
                parts.getOrNull(0)
                    ?.takeIf {
                        it.isNotBlank()
                    },
            renderer =
                parts.getOrNull(1)
                    ?.takeIf {
                        it.isNotBlank()
                    },
            openGlVersion =
                parts.drop(2)
                    .joinToString(", ")
                    .takeIf {
                        it.isNotBlank()
                    },
            vulkanSupported =
                vulkan,
            vulkanApiVersion = api,
            usagePercent = usage,
            currentHz =
                normalize(
                    freq.getOrNull(1)
                        ?.toLongOrNull(),
                ),
            minHz =
                normalize(
                    freq.getOrNull(2)
                        ?.toLongOrNull(),
                ),
            maxHz =
                normalize(
                    freq.getOrNull(3)
                        ?.toLongOrNull(),
                ),
        )
    }

    private fun normalize(
        value: Long?,
    ): Long? {
        value ?: return null
        if (value <= 0L) return null
        return when {
            value < 10_000L ->
                value *
                    1_000_000L
            value < 10_000_000L ->
                value * 1_000L
            else -> value
        }
    }
}
),
            )
        val busy =
            raw.lineSequence()
                .firstOrNull {
                    it.startsWith(
                        "__BUSY__|",
                    )
                }
                ?.substringAfter('|')
                .orEmpty()
        val freq =
            raw.lineSequence()
                .firstOrNull {
                    it.startsWith(
                        "__FREQ__|",
                    )
                }
                ?.split('|')
                .orEmpty()
        val numbers =
            Regex("\d+")
                .findAll(busy)
                .mapNotNull {
                    it.value
                        .toLongOrNull()
                }
                .toList()
        val usage =
            when {
                numbers.size >= 2 &&
                    numbers[1] > 0L ->
                    (
                        numbers[0] *
                            100f /
                            numbers[1]
                        ).coerceIn(
                        0f,
                        100f,
                    )
                numbers.isNotEmpty() &&
                    numbers[0] <= 100L ->
                    numbers[0]
                        .toFloat()
                else -> null
            }
        val glLine =
            raw.lineSequence()
                .firstOrNull {
                    !it.startsWith("__") &&
                        (
                            it.contains(
                                "GLES:",
                                true,
                            ) ||
                                it.contains(
                                    "GL_RENDERER",
                                    true,
                                )
                            )
                }
                .orEmpty()
        val parts =
            glLine.substringAfter(
                "GLES:",
                glLine,
            ).split(',')
                .map {
                    it.trim()
                }

        val vulkan =
            packageManager.hasSystemFeature(
                PackageManager
                    .FEATURE_VULKAN_HARDWARE_LEVEL,
            ) ||
                packageManager.hasSystemFeature(
                    PackageManager
                        .FEATURE_VULKAN_HARDWARE_VERSION,
                )
        val api =
            packageManager
                .systemAvailableFeatures
                .firstOrNull {
                    it.name ==
                        PackageManager
                            .FEATURE_VULKAN_HARDWARE_VERSION
                }?.version
                ?.let {
                    value ->
                    val major =
                        value ushr 22
                    val minor =
                        (
                            value ushr 12
                            ) and 0x3ff
                    val patch =
                        value and 0xfff
                    "$major.$minor.$patch"
                }

        return YTaskGpuSnapshot(
            vendor =
                parts.getOrNull(0)
                    ?.takeIf {
                        it.isNotBlank()
                    },
            renderer =
                parts.getOrNull(1)
                    ?.takeIf {
                        it.isNotBlank()
                    },
            openGlVersion =
                parts.drop(2)
                    .joinToString(", ")
                    .takeIf {
                        it.isNotBlank()
                    },
            vulkanSupported =
                vulkan,
            vulkanApiVersion = api,
            usagePercent = usage,
            currentHz =
                normalize(
                    freq.getOrNull(1)
                        ?.toLongOrNull(),
                ),
            minHz =
                normalize(
                    freq.getOrNull(2)
                        ?.toLongOrNull(),
                ),
            maxHz =
                normalize(
                    freq.getOrNull(3)
                        ?.toLongOrNull(),
                ),
        )
    }

    private fun normalize(
        value: Long?,
    ): Long? {
        value ?: return null
        if (value <= 0L) return null
        return when {
            value < 10_000L ->
                value *
                    1_000_000L
            value < 10_000_000L ->
                value * 1_000L
            else -> value
        }
    }
}
