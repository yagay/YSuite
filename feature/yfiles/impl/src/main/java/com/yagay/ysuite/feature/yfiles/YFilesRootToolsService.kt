package com.yagay.ysuite.feature.yfiles

import android.content.Context
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class YRootFileSecurityInfo(
    val path: String,
    val selinuxContext: String?,
    val mountLine: String?,
)

enum class YRootModuleManager {
    Magisk,
    KernelSU,
    APatch,
    Unknown,
}

data class YRootModule(
    val id: String,
    val name: String,
    val version: String?,
    val author: String?,
    val description: String?,
    val disabled: Boolean,
    val removeOnReboot: Boolean,
    val manager: YRootModuleManager,
)

data class YEncryptedVolumeSupport(
    val gocryptfs: Boolean,
    val encfs: Boolean,
    val fusermount: Boolean,
)

class YFilesRootToolsService(
    context: Context,
    private val root: RootGateway,
) {
    private val appContext =
        context.applicationContext
    suspend fun securityInfo(
        path: String,
    ): Outcome<YRootFileSecurityInfo> =
        rootText(
            """
            p=${quote(path)}
            ctx=$(ls -Zd -- "$p" 2>/dev/null | awk '{print $1}')
            mnt=$(mount 2>/dev/null | awk -v p="$p" 'index(p,$3)==1 { if(length($3)>best){best=length($3); line=$0} } END{print line}')
            printf '%s\n%s\n' "$ctx" "$mnt"
            """.trimIndent(),
            "root_security_info_failed",
        ) { stdout ->
            val lines = stdout.lines()
            YRootFileSecurityInfo(
                path = path,
                selinuxContext =
                    lines.getOrNull(0)
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        },
                mountLine =
                    lines.getOrNull(1)
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        },
            )
        }

    suspend fun remount(
        mountPoint: String,
        writable: Boolean,
    ): Outcome<Unit> =
        rootUnit(
            "mount -o " +
                (
                    if (writable) {
                        "rw"
                    } else {
                        "ro"
                    }
                    ) +
                ",remount -- " +
                quote(mountPoint),
            "root_remount_failed",
        )

    suspend fun modules():
        Outcome<List<YRootModule>> =
        rootText(
            """
            manager=unknown
            command -v magisk >/dev/null 2>&1 && manager=magisk
            command -v ksud >/dev/null 2>&1 && manager=kernelsu
            command -v apd >/dev/null 2>&1 && manager=apatch
            base=/data/adb/modules
            [ -d "$base" ] || exit 0
            for d in "$base"/*; do
              [ -d "$d" ] || continue
              id=$(basename "$d")
              name=$(sed -n 's/^name=//p' "$d/module.prop" 2>/dev/null | head -n1)
              version=$(sed -n 's/^version=//p' "$d/module.prop" 2>/dev/null | head -n1)
              author=$(sed -n 's/^author=//p' "$d/module.prop" 2>/dev/null | head -n1)
              description=$(sed -n 's/^description=//p' "$d/module.prop" 2>/dev/null | head -n1)
              [ -e "$d/disable" ] && disabled=1 || disabled=0
              [ -e "$d/remove" ] && remove=1 || remove=0
              printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$manager" "$id" "$name" "$version" "$author" "$description" "$disabled" "$remove"
            done
            """.trimIndent(),
            "root_modules_failed",
        ) { stdout ->
            stdout.lineSequence()
                .filter {
                    it.isNotBlank()
                }
                .mapNotNull { line ->
                    val parts =
                        line.split(
                            '\t',
                            limit = 8,
                        )
                    if (parts.size < 8) {
                        return@mapNotNull null
                    }
                    YRootModule(
                        id = parts[1],
                        name =
                            parts[2]
                                .ifBlank {
                                    parts[1]
                                },
                        version =
                            parts[3]
                                .takeIf {
                                    it.isNotBlank()
                                },
                        author =
                            parts[4]
                                .takeIf {
                                    it.isNotBlank()
                                },
                        description =
                            parts[5]
                                .takeIf {
                                    it.isNotBlank()
                                },
                        disabled =
                            parts[6] == "1",
                        removeOnReboot =
                            parts[7] == "1",
                        manager =
                            when (
                                parts[0]
                                    .lowercase()
                            ) {
                                "magisk" ->
                                    YRootModuleManager
                                        .Magisk
                                "kernelsu" ->
                                    YRootModuleManager
                                        .KernelSU
                                "apatch" ->
                                    YRootModuleManager
                                        .APatch
                                else ->
                                    YRootModuleManager
                                        .Unknown
                            },
                    )
                }.sortedBy {
                    it.name.lowercase()
                }.toList()
        }

    suspend fun setModuleEnabled(
        id: String,
        enabled: Boolean,
    ): Outcome<Unit> {
        val safe = safeModuleId(id)
        val marker =
            "/data/adb/modules/" +
                safe +
                "/disable"
        return if (enabled) {
            rootUnit(
                "rm -f -- " +
                    quote(marker),
                "root_module_toggle_failed",
            )
        } else {
            rootUnit(
                "touch -- " +
                    quote(marker),
                "root_module_toggle_failed",
            )
        }
    }

    suspend fun markModuleForRemoval(
        id: String,
        remove: Boolean,
    ): Outcome<Unit> {
        val safe = safeModuleId(id)
        val marker =
            "/data/adb/modules/" +
                safe +
                "/remove"
        return if (remove) {
            rootUnit(
                "touch -- " +
                    quote(marker),
                "root_module_remove_failed",
            )
        } else {
            rootUnit(
                "rm -f -- " +
                    quote(marker),
                "root_module_remove_failed",
            )
        }
    }

    suspend fun installModule(
        zipPath: String,
    ): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            val manager =
                when (
                    val probe =
                        execute(
                            """
                            if command -v magisk >/dev/null 2>&1; then echo magisk
                            elif command -v ksud >/dev/null 2>&1; then echo kernelsu
                            elif command -v apd >/dev/null 2>&1; then echo apatch
                            else echo none
                            fi
                            """.trimIndent(),
                        )
                ) {
                    is Outcome.Success ->
                        probe.value.stdout
                            .trim()
                    is Outcome.Failure ->
                        return@withContext
                            probe
                }
            val command =
                when (manager) {
                    "magisk" ->
                        "magisk --install-module " +
                            quote(zipPath)
                    "kernelsu" ->
                        "ksud module install " +
                            quote(zipPath)
                    "apatch" ->
                        "apd module install " +
                            quote(zipPath)
                    else ->
                        return@withContext
                            Outcome.Failure(
                                code =
                                    "root_module_manager_missing",
                                message =
                                    appContext.getString(
                                        R.string.yfiles_msg_no_root_module_manager,
                                    ),
                            )
                }
            rootUnit(
                command,
                "root_module_install_failed",
            )
        }

    suspend fun encryptedVolumeSupport():
        Outcome<YEncryptedVolumeSupport> =
        rootText(
            """
            command -v gocryptfs >/dev/null 2>&1 && echo gocryptfs=1 || echo gocryptfs=0
            command -v encfs >/dev/null 2>&1 && echo encfs=1 || echo encfs=0
            command -v fusermount >/dev/null 2>&1 && echo fusermount=1 || echo fusermount=0
            """.trimIndent(),
            "encrypted_volume_probe_failed",
        ) { stdout ->
            YEncryptedVolumeSupport(
                gocryptfs =
                    "gocryptfs=1" in stdout,
                encfs =
                    "encfs=1" in stdout,
                fusermount =
                    "fusermount=1" in stdout,
            )
        }

    suspend fun mountEncryptedVolume(
        type: String,
        source: String,
        mountPoint: String,
        passphrase: String,
    ): Outcome<Unit> {
        val command =
            when (type.lowercase()) {
                "gocryptfs" ->
                    "mkdir -p " +
                        quote(mountPoint) +
                        " && printf '%s\n' " +
                        quote(passphrase) +
                        " | gocryptfs -q " +
                        quote(source) +
                        " " +
                        quote(mountPoint)
                "encfs" ->
                    "mkdir -p " +
                        quote(mountPoint) +
                        " && printf '%s\n' " +
                        quote(passphrase) +
                        " | encfs --stdinpass " +
                        quote(source) +
                        " " +
                        quote(mountPoint)
                else ->
                    return Outcome.Failure(
                        code =
                            "encrypted_volume_type",
                        message =
                            appContext.getString(
                                R.string.yfiles_msg_unsupported_volume_type,
                            ),
                    )
            }
        return rootUnit(
            command,
            "encrypted_volume_mount_failed",
        )
    }

    suspend fun unmountEncryptedVolume(
        mountPoint: String,
    ): Outcome<Unit> =
        rootUnit(
            "fusermount -u " +
                quote(mountPoint) +
                " 2>/dev/null || umount " +
                quote(mountPoint),
            "encrypted_volume_unmount_failed",
        )

    private suspend fun execute(
        command: String,
    ) =
        root.execute(
            RootRequest(
                command = command,
                timeoutMillis =
                    ROOT_TOOL_TIMEOUT,
            ),
        )

    private suspend fun rootUnit(
        command: String,
        code: String,
    ): Outcome<Unit> =
        when (
            val result =
                execute(command)
        ) {
            is Outcome.Success ->
                if (
                    result.value.exitCode == 0
                ) {
                    Outcome.Success(Unit)
                } else {
                    Outcome.Failure(
                        code = code,
                        message =
                            result.value.stderr
                                .trim()
                                .ifBlank {
                                    "Root command failed"
                                },
                    )
                }
            is Outcome.Failure -> result
        }

    private suspend fun <T> rootText(
        command: String,
        code: String,
        transform: (String) -> T,
    ): Outcome<T> =
        when (
            val result =
                execute(command)
        ) {
            is Outcome.Success ->
                if (
                    result.value.exitCode == 0
                ) {
                    runCatching {
                        transform(
                            result.value.stdout,
                        )
                    }.fold(
                        onSuccess = {
                            Outcome.Success(it)
                        },
                        onFailure = {
                            Outcome.Failure(
                                code = code,
                                message =
                                    it.message
                                        ?: "Unable to parse root command result",
                                cause = it,
                            )
                        },
                    )
                } else {
                    Outcome.Failure(
                        code = code,
                        message =
                            result.value.stderr
                                .trim()
                                .ifBlank {
                                    "Root command failed"
                                },
                    )
                }
            is Outcome.Failure -> result
        }

    private fun safeModuleId(
        value: String,
    ): String {
        require(
            value.matches(
                Regex(
                    "[A-Za-z0-9._-]+",
                ),
            ),
        ) {
            "Invalid module id"
        }
        return value
    }

    private fun quote(
        value: String,
    ): String =
        "'" +
            value.replace(
                "'",
                "'\"'\"'",
            ) +
            "'"

    companion object {
        private const val ROOT_TOOL_TIMEOUT =
            60_000L
    }
}
