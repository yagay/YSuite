package com.yagay.ysuite.feature.ypower.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Debug
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.File
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class YPowerModule : XposedModule() {
    private var packageName = ""
    private var processName = ""
    private val installed = ConcurrentHashMap.newKeySet<String>()
    private lateinit var profile: Profile

    override fun onModuleLoaded(
        param: XposedModuleInterface.ModuleLoadedParam,
    ) {
        processName = param.processName
    }

    override fun onPackageReady(
        param: XposedModuleInterface.PackageReadyParam,
    ) {
        if (!param.isFirstPackage) return
        packageName = param.packageName
        profile = loadProfile(packageName)
        if (!profile.enabled) return

        if (profile.simulateSystemApp) installIdentityHooks()
        if (profile.simulatePermissions || profile.tracePermissions) {
            installPermissionHooks()
        }
        if (profile.tracePackageScan) installPackageHooks()
        if (profile.traceFiles) installFileHooks()
        if (profile.traceCommands) installCommandHooks()
        if (profile.traceProperties) installPropertyHooks()
        if (profile.traceDebugger) installDebuggerHooks()
        if (profile.traceExceptions) installExceptionHooks()
        if (profile.traceSecurityApis) installSecurityHooks()
        if (profile.traceNative) installNativeHooks()
        if (profile.traceSyscalls) installSyscallHooks()

        trace(
            "module",
            "enabled identity=" + profile.simulateSystemApp +
                " permissions=" + profile.simulatePermissions,
        )
    }

    @Synchronized
    override fun onHotReloading(
        param: XposedModuleInterface.HotReloadingParam,
    ): Boolean = true

    override fun onHotReloaded(
        param: XposedModuleInterface.HotReloadedParam,
    ) {
        param.oldHookHandles.forEach { runCatching { it.unhook() } }
        installed.clear()
        processName = param.processName
        packageName =
            if (param.isSystemServer) "system"
            else param.processName.substringBefore(':')
        profile = loadProfile(packageName)
    }

    private fun loadProfile(pkg: String): Profile =
        runCatching {
            val raw =
                getRemotePreferences(GROUP)
                    .getString(
                        "profile:" + pkg,
                        null,
                    )
            Profile.from(raw)
        }.getOrDefault(Profile())

    private fun installIdentityHooks() {
        hookMethod(
            "application_info_system",
            runCatching {
                ApplicationInfo::class.java
                    .getDeclaredMethod("isSystemApp")
            }.getOrNull(),
        ) { chain ->
            val info =
                chain.thisObject as? ApplicationInfo
            if (info?.packageName == packageName) {
                true
            } else {
                chain.proceed()
            }
        }
        hookMethod(
            "application_info_updated_system",
            runCatching {
                ApplicationInfo::class.java
                    .getDeclaredMethod(
                        "isUpdatedSystemApp",
                    )
            }.getOrNull(),
        ) { chain ->
            val info =
                chain.thisObject as? ApplicationInfo
            if (info?.packageName == packageName) {
                true
            } else {
                chain.proceed()
            }
        }

        val apm =
            runCatching {
                Class.forName(
                    "android.app.ApplicationPackageManager",
                )
            }.getOrNull()
                ?: return
        apm.declaredMethods
            .filter {
                it.name ==
                    "getApplicationInfo"
            }
            .forEach { method ->
                hookMethod(
                    "apm_getApplicationInfo_" +
                        method.parameterCount,
                    method,
                ) { chain ->
                    val result = chain.proceed()
                    val info =
                        result as? ApplicationInfo
                            ?: return@hookMethod result
                    if (
                        info.packageName !=
                        packageName
                    ) {
                        result
                    } else {
                        ApplicationInfo(info).apply {
                            flags =
                                flags or
                                    ApplicationInfo
                                        .FLAG_SYSTEM or
                                    ApplicationInfo
                                        .FLAG_UPDATED_SYSTEM_APP
                        }
                    }
                }
            }
    }

    private fun installPermissionHooks() {
        hookMethod(
            "context_checkSelfPermission",
            runCatching {
                Context::class.java
                    .getDeclaredMethod(
                        "checkSelfPermission",
                        String::class.java,
                    )
            }.getOrNull(),
        ) { chain ->
            val permission =
                chain.args.getOrNull(0)
                    ?.toString()
                    .orEmpty()
            val original = chain.proceed()
            if (profile.tracePermissions) {
                trace(
                    "permission",
                    "checkSelfPermission " +
                        permission +
                        " result=" +
                        original,
                )
            }
            if (profile.simulatePermissions) {
                PackageManager.PERMISSION_GRANTED
            } else {
                original
            }
        }

        val apm =
            runCatching {
                Class.forName(
                    "android.app.ApplicationPackageManager",
                )
            }.getOrNull()
                ?: return
        apm.declaredMethods
            .filter {
                it.name ==
                    "checkPermission" &&
                    it.parameterCount >= 2
            }
            .forEach { method ->
                hookMethod(
                    "apm_checkPermission_" +
                        method.parameterCount,
                    method,
                ) { chain ->
                    val permission =
                        chain.args
                            .getOrNull(0)
                            ?.toString()
                            .orEmpty()
                    val queried =
                        chain.args
                            .getOrNull(1)
                            ?.toString()
                            .orEmpty()
                    val original =
                        chain.proceed()
                    if (
                        profile
                            .tracePermissions
                    ) {
                        trace(
                            "permission",
                            "checkPermission " +
                                permission +
                                " target=" +
                                queried +
                                " result=" +
                                original,
                        )
                    }
                    if (
                        profile
                            .simulatePermissions &&
                        queried ==
                        packageName
                    ) {
                        PackageManager
                            .PERMISSION_GRANTED
                    } else {
                        original
                    }
                }
            }
    }

    private fun installPackageHooks() {
        val apm =
            runCatching {
                Class.forName(
                    "android.app.ApplicationPackageManager",
                )
            }.getOrNull()
                ?: return
        val names =
            setOf(
                "getApplicationInfo",
                "getPackageInfo",
                "getInstalledApplications",
                "getInstalledPackages",
                "queryIntentActivities",
                "queryIntentServices",
                "resolveActivity",
            )
        apm.declaredMethods
            .filter { it.name in names }
            .forEach { method ->
                hookTrace(
                    "package_" +
                        method.name +
                        "_" +
                        method.parameterCount,
                    method,
                    "package",
                )
            }
    }

    private fun installFileHooks() {
        for (
            name in
            listOf(
                "exists",
                "canRead",
                "canExecute",
                "list",
                "listFiles",
            )
        ) {
            hookTrace(
                "file_" + name,
                runCatching {
                    File::class.java
                        .getDeclaredMethod(name)
                }.getOrNull(),
                "file",
            ) { chain ->
                val file =
                    chain.thisObject as? File
                file?.absolutePath.orEmpty()
            }
        }
    }

    private fun installCommandHooks() {
        Runtime::class.java.declaredMethods
            .filter { it.name == "exec" }
            .forEach { method ->
                hookTrace(
                    "runtime_exec_" +
                        method.parameterCount,
                    method,
                    "command",
                ) { chain ->
                    chain.args
                        .joinToString(" ") {
                            it?.toString().orEmpty()
                        }
                }
            }
        hookTrace(
            "process_builder_start",
            runCatching {
                ProcessBuilder::class.java
                    .getDeclaredMethod("start")
            }.getOrNull(),
            "command",
        ) { chain ->
            val builder =
                chain.thisObject as?
                    ProcessBuilder
            builder?.command()
                ?.joinToString(" ")
                .orEmpty()
        }
    }

    private fun installPropertyHooks() {
        System::class.java.declaredMethods
            .filter {
                it.name == "getProperty" ||
                    it.name == "getenv"
            }
            .forEach { method ->
                hookTrace(
                    "system_property_" +
                        method.name +
                        "_" +
                        method.parameterCount,
                    method,
                    "property",
                )
            }
        val systemProperties =
            runCatching {
                Class.forName(
                    "android.os.SystemProperties",
                )
            }.getOrNull()
                ?: return
        systemProperties.declaredMethods
            .filter {
                it.name in
                    setOf(
                        "get",
                        "getInt",
                        "getLong",
                        "getBoolean",
                    )
            }
            .forEach { method ->
                hookTrace(
                    "android_property_" +
                        method.name +
                        "_" +
                        method.parameterCount,
                    method,
                    "property",
                )
            }
    }

    private fun installDebuggerHooks() {
        for (
            name in
            listOf(
                "isDebuggerConnected",
                "waitingForDebugger",
            )
        ) {
            hookTrace(
                "debug_" + name,
                runCatching {
                    Debug::class.java
                        .getDeclaredMethod(name)
                }.getOrNull(),
                "debugger",
            )
        }
    }

    private fun installExceptionHooks() {
        hookTrace(
            "threadgroup_uncaught",
            runCatching {
                ThreadGroup::class.java
                    .getDeclaredMethod(
                        "uncaughtException",
                        Thread::class.java,
                        Throwable::class.java,
                    )
            }.getOrNull(),
            "exception",
        ) { chain ->
            val error =
                chain.args.getOrNull(1)
                    as? Throwable
            (error?.javaClass?.name ?: "") +
                ":" +
                (error?.message ?: "")
        }
    }

    private fun installSecurityHooks() {
        KeyStore::class.java.declaredMethods
            .filter {
                it.name in
                    setOf(
                        "getInstance",
                        "load",
                        "containsAlias",
                    )
            }
            .forEach { method ->
                hookTrace(
                    "keystore_" +
                        method.name +
                        "_" +
                        method.parameterCount,
                    method,
                    "security",
                )
            }
        MessageDigest::class.java
            .declaredMethods
            .filter {
                it.name ==
                    "getInstance"
            }
            .forEach { method ->
                hookTrace(
                    "digest_" +
                        method.parameterCount,
                    method,
                    "security",
                )
            }
    }

    private fun installNativeHooks() {
        System::class.java.declaredMethods
            .filter {
                it.name ==
                    "load" ||
                    it.name ==
                    "loadLibrary"
            }
            .forEach { method ->
                hookTrace(
                    "native_" +
                        method.name +
                        "_" +
                        method.parameterCount,
                    method,
                    "native",
                )
            }
    }

    private fun installSyscallHooks() {
        val os =
            runCatching {
                Class.forName(
                    "android.system.Os",
                )
            }.getOrNull()
                ?: return
        os.declaredMethods
            .filter {
                it.name in
                    setOf(
                        "open",
                        "read",
                        "write",
                        "stat",
                        "lstat",
                        "access",
                        "connect",
                        "socket",
                        "execv",
                        "execve",
                    )
            }
            .forEach { method ->
                hookTrace(
                    "syscall_" +
                        method.name +
                        "_" +
                        method.parameterCount,
                    method,
                    "syscall",
                )
            }
    }

    private fun hookTrace(
        id: String,
        executable: Executable?,
        type: String,
        detail:
            ((XposedInterface.Chain) -> String)? =
            null,
    ) {
        hookMethod(id, executable) { chain ->
            val input =
                runCatching {
                    detail?.invoke(chain)
                        ?: chain.args
                            .joinToString(" ") {
                                it?.toString()
                                    .orEmpty()
                                    .take(200)
                            }
                }.getOrDefault("")
            val started =
                System.nanoTime()
            try {
                val result = chain.proceed()
                trace(
                    type,
                    input +
                        " result=" +
                        summarize(result) +
                        " durationNs=" +
                        (
                            System.nanoTime() -
                                started
                            ),
                )
                result
            } catch (error: Throwable) {
                trace(
                    type,
                    input +
                        " exception=" +
                        error.javaClass.name,
                )
                throw error
            }
        }
    }

    private fun hookMethod(
        id: String,
        executable: Executable?,
        callback:
            (XposedInterface.Chain) -> Any?,
    ) {
        if (
            executable == null ||
            !installed.add(id)
        ) {
            return
        }
        runCatching {
            executable.isAccessible = true
            hook(executable)
                .setId("ypower-" + id)
                .intercept(
                    XposedInterface.Hooker {
                        chain ->
                        callback(chain)
                    },
                )
        }.onFailure {
            installed.remove(id)
            Log.w(
                TAG,
                "hook_failed id=" + id,
                it,
            )
        }
    }

    private fun trace(
        type: String,
        detail: String,
    ) {
        val stack =
            if (profile.traceStacks) {
                Throwable().stackTrace
                    .drop(2)
                    .take(16)
                    .joinToString(" <- ") {
                        it.className +
                            "#" +
                            it.methodName +
                            ":" +
                            it.lineNumber
                    }
            } else {
                ""
            }
        Log.i(
            TAG,
            "{\"package\":\"" +
                escape(packageName) +
                "\",\"process\":\"" +
                escape(processName) +
                "\",\"type\":\"" +
                escape(type) +
                "\",\"detail\":\"" +
                escape(detail.take(2_000)) +
                "\",\"stack\":\"" +
                escape(stack) +
                "\"}",
        )
    }

    private fun summarize(value: Any?): String =
        when (value) {
            null -> "null"
            is Boolean,
            is Number,
            is CharSequence ->
                value.toString()
            is Collection<*> ->
                "collection(size=" +
                    value.size +
                    ")"
            is Array<*> ->
                "array(size=" +
                    value.size +
                    ")"
            else ->
                value.javaClass.name
        }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    private data class Profile(
        val enabled: Boolean = false,
        val simulateSystemApp: Boolean = false,
        val simulatePermissions: Boolean = false,
        val tracePackageScan: Boolean = false,
        val traceFiles: Boolean = false,
        val traceCommands: Boolean = false,
        val traceProperties: Boolean = false,
        val tracePermissions: Boolean = false,
        val traceDebugger: Boolean = false,
        val traceExceptions: Boolean = false,
        val traceSecurityApis: Boolean = false,
        val traceNative: Boolean = false,
        val traceSyscalls: Boolean = false,
        val traceStacks: Boolean = true,
    ) {
        companion object {
            fun from(raw: String?): Profile {
                if (raw.isNullOrBlank()) {
                    return Profile()
                }
                return runCatching {
                    val value = JSONObject(raw)
                    Profile(
                        enabled =
                            value.optBoolean(
                                "enabled",
                                false,
                            ),
                        simulateSystemApp =
                            value.optBoolean(
                                "simulateSystemApp",
                                false,
                            ),
                        simulatePermissions =
                            value.optBoolean(
                                "simulatePermissions",
                                false,
                            ),
                        tracePackageScan =
                            value.optBoolean(
                                "tracePackageScan",
                                false,
                            ),
                        traceFiles =
                            value.optBoolean(
                                "traceFiles",
                                false,
                            ),
                        traceCommands =
                            value.optBoolean(
                                "traceCommands",
                                false,
                            ),
                        traceProperties =
                            value.optBoolean(
                                "traceProperties",
                                false,
                            ),
                        tracePermissions =
                            value.optBoolean(
                                "tracePermissions",
                                false,
                            ),
                        traceDebugger =
                            value.optBoolean(
                                "traceDebugger",
                                false,
                            ),
                        traceExceptions =
                            value.optBoolean(
                                "traceExceptions",
                                false,
                            ),
                        traceSecurityApis =
                            value.optBoolean(
                                "traceSecurityApis",
                                false,
                            ),
                        traceNative =
                            value.optBoolean(
                                "traceNative",
                                false,
                            ),
                        traceSyscalls =
                            value.optBoolean(
                                "traceSyscalls",
                                false,
                            ),
                        traceStacks =
                            value.optBoolean(
                                "traceStacks",
                                true,
                            ),
                    )
                }.getOrDefault(Profile())
            }
        }
    }

    companion object {
        private const val TAG = "YPowerTrace"
        private const val GROUP = "ypower"
    }
}
