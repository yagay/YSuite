package com.yagay.yfiles

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.FeatureHostBinding
import com.yagay.suite.api.HostCapability
import com.yagay.suite.api.HostCapabilityRequestResult
import com.yagay.suite.api.HostCapabilityState
import com.yagay.suite.api.HostLogLevel
import com.yagay.suite.api.ManagedFeatureRuntime
import java.io.File

class YFilesSuiteRuntime private constructor(context: Context) : ManagedFeatureRuntime {
    private val appContext = context.applicationContext
    override fun attach(host: FeatureHost) { hostBinding.attach(host) }
    override fun enable() { log(HostLogLevel.INFO, "yfiles runtime enabled") }
    override fun disable() { log(HostLogLevel.INFO, "yfiles runtime disabled") }
    override fun destroy() { hostBinding.clearIfOwnedBy(appContext) }

    companion object {
        @Volatile private var instance: YFilesSuiteRuntime? = null
        private val hostBinding = FeatureHostBinding("YFiles")

        @JvmStatic fun get(context: Context): YFilesSuiteRuntime = instance ?: synchronized(this) {
            instance ?: YFilesSuiteRuntime(context).also { instance = it }
        }

        fun capabilityState(capability: HostCapability): HostCapabilityState =
            hostBinding.capabilityState(capability)

        fun requestCapability(
            activity: Activity,
            capability: HostCapability,
        ): HostCapabilityRequestResult =
            hostBinding.requestCapability(activity, capability)

        fun rootAvailable(): Boolean =
            capabilityState(HostCapability.ROOT) == HostCapabilityState.GRANTED

        fun sharedFileUri(file: File): Uri? = hostBinding.sharedFileUri(file)

        fun rootList(path: String): Result<List<String>> = rootCommand(
            operation = "list",
            command = "ls -A1 -- ${shellQuote(path)}",
            timeoutSeconds = 12L,
        ).map { output -> output.lineSequence().filter(String::isNotBlank).toList() }

        fun rootIsDirectory(path: String): Result<Boolean> = rootCommand(
            operation = "is-directory",
            command = "if [ -d ${shellQuote(path)} ]; then printf 1; else printf 0; fi",
        ).map { it.trim() == "1" }

        fun rootCreateFolder(parent: String, name: String): Result<Unit> = runCatching {
            val target = rootChild(parent, validateRootName(name))
            rootCommand(
                operation = "mkdir",
                command = guardedCreateCommand(target, "mkdir -- ${shellQuote(target)}"),
            ).getOrThrow()
        }

        fun rootCreateFile(parent: String, name: String): Result<Unit> = runCatching {
            val target = rootChild(parent, validateRootName(name))
            rootCommand(
                operation = "touch",
                command = guardedCreateCommand(target, ": > ${shellQuote(target)}"),
            ).getOrThrow()
        }

        fun rootRename(path: String, newName: String): Result<String> = runCatching {
            val parent = path.substringBeforeLast('/', missingDelimiterValue = "/").ifBlank { "/" }
            val target = rootChild(parent, validateRootName(newName))
            require(target != path) { "Name is unchanged" }
            rootCommand(
                operation = "rename",
                command = "if [ -e ${shellQuote(target)} ] || [ -L ${shellQuote(target)} ]; then " +
                    "printf 'Target already exists' >&2; exit 17; fi; " +
                    "mv -- ${shellQuote(path)} ${shellQuote(target)}",
                timeoutSeconds = 30L,
            ).getOrThrow()
            target
        }

        fun rootTransfer(
            sourcePath: String,
            destinationDirectory: String,
            mode: FileTransferMode,
        ): Result<String> = runCatching {
            require(sourcePath != "/") { "Root directory cannot be copied or moved" }
            val name = sourcePath.trimEnd('/').substringAfterLast('/')
            require(name.isNotBlank()) { "Invalid source path" }
            val target = rootChild(destinationDirectory, name)
            val source = shellQuote(sourcePath)
            val destination = shellQuote(target)
            val command = when (mode) {
                FileTransferMode.COPY -> "cp -a -- $source $destination"
                FileTransferMode.MOVE -> "mv -- $source $destination"
            }
            rootCommand(
                operation = if (mode == FileTransferMode.COPY) "copy" else "move",
                command = "if [ -e $destination ] || [ -L $destination ]; then " +
                    "printf 'Target already exists' >&2; exit 17; fi; $command",
                timeoutSeconds = 120L,
            ).getOrThrow()
            target
        }

        fun rootDelete(path: String): Result<Unit> = runCatching {
            require(path != "/") { "Root directory cannot be deleted" }
            rootCommand(
                operation = "delete",
                command = "rm -rf -- ${shellQuote(path)}",
                timeoutSeconds = 120L,
            ).getOrThrow()
        }

        fun rootStageFile(path: String): Result<File> = runCatching {
            require(!rootIsDirectory(path).getOrThrow()) { "Folders cannot be opened or shared directly" }
            val current = hostBinding.requireHost("YSuite Root host is not attached")
            val cacheDirectory = File(current.applicationContext.cacheDir, "root-share")
            require(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) { "Unable to create share cache" }
            cacheDirectory.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > ROOT_SHARE_MAX_AGE_MS) runCatching { file.delete() }
            }
            val originalName = path.substringAfterLast('/').ifBlank { "root-file" }
            val safeName = originalName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(96).ifBlank { "root-file" }
            val target = File(cacheDirectory, "${System.currentTimeMillis()}-$safeName")
            val uid = current.applicationContext.applicationInfo.uid
            val targetQuoted = shellQuote(target.absolutePath)
            rootCommand(
                operation = "stage-file",
                command = "cp -f -- ${shellQuote(path)} $targetQuoted && " +
                    "chown $uid:$uid $targetQuoted && chmod 600 $targetQuoted",
                timeoutSeconds = 60L,
            ).getOrThrow()
            require(target.isFile) { "Root file staging failed" }
            target
        }

        fun log(level: HostLogLevel, message: String, error: Throwable? = null) {
            hostBinding.log(level, message, error)
        }

        private fun rootCommand(
            operation: String,
            command: String,
            timeoutSeconds: Long = 20L,
        ): Result<String> = hostBinding.rootText(operation, command, timeoutSeconds)

        private fun guardedCreateCommand(target: String, createCommand: String): String {
            val quoted = shellQuote(target)
            return "if [ -e $quoted ] || [ -L $quoted ]; then " +
                "printf 'Target already exists' >&2; exit 17; fi; $createCommand"
        }

        private fun validateRootName(raw: String): String {
            val value = raw.trim()
            require(value.isNotBlank()) { "Name is empty" }
            require(value != "." && value != "..") { "Invalid name" }
            require('/' !in value && '\u0000' !in value) { "Name must not contain / or NUL" }
            return value
        }

        private fun rootChild(parent: String, name: String): String =
            if (parent == "/") "/$name" else parent.trimEnd('/') + "/" + name

        private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        private const val ROOT_SHARE_MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
