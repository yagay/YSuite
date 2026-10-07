package com.yagay.ysuite.feature.ydiag.runtime

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import com.yagay.ysuite.runtime.RuntimeOwnerGate
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class YDiagModule : XposedModule() {
    private var processName = ""
    private var moduleHostPackage = ""
    private var packageName = ""
    private val installed = ConcurrentHashMap.newKeySet<String>()
    private val hits = ConcurrentHashMap<String, AtomicLong>()

    @Volatile private var tracked = false
    @Volatile private var options: Set<String> = emptySet()
    @Volatile private var listenerRegistered = false

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(GROUP)
    }

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY_TARGETS || key == KEY_OPTIONS) {
                refreshConfiguration("preference_changed")
            }
        }

    override fun onModuleLoaded(
        param: XposedModuleInterface.ModuleLoadedParam,
    ) {
        processName = param.processName
        moduleHostPackage =
            runCatching {
                getModuleApplicationInfo()
                    ?.packageName
                    .orEmpty()
            }.getOrDefault("")
        RuntimeOwnerGate.announce(
            "ydiag",
            moduleHostPackage,
        )
        Log.i(
            TAG,
            "MODULE_LOADED process=" +
                processName +
                " host=" +
                moduleHostPackage,
        )
    }

    override fun onPackageReady(
        param: XposedModuleInterface.PackageReadyParam,
    ) {
        if (
            !RuntimeOwnerGate.shouldRun(
                "ydiag",
                moduleHostPackage,
            )
        ) return
        packageName = param.packageName
        registerListener()
        refreshConfiguration("package_ready")
    }

    override fun onSystemServerStarting(
        param: XposedModuleInterface.SystemServerStartingParam,
    ) {
        if (
            !RuntimeOwnerGate.shouldRun(
                "ydiag",
                moduleHostPackage,
            )
        ) return
        packageName = "system"
        registerListener()
        refreshConfiguration("system_server_ready")
    }

    @Synchronized
    override fun onHotReloading(
        param: XposedModuleInterface.HotReloadingParam,
    ): Boolean {
        if (listenerRegistered) {
            runCatching {
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
            listenerRegistered = false
        }
        return true
    }

    override fun onHotReloaded(
        param: XposedModuleInterface.HotReloadedParam,
    ) {
        processName = param.processName
        packageName =
            if (param.isSystemServer) "system"
            else param.processName.substringBefore(':')
        param.oldHookHandles.forEach { runCatching { it.unhook() } }
        installed.clear()
        hits.clear()
        listenerRegistered = false
        registerListener()
        refreshConfiguration("hot_reloaded")
    }

    @Synchronized
    private fun registerListener() {
        if (listenerRegistered) return
        runCatching {
            preferences.registerOnSharedPreferenceChangeListener(listener)
            listenerRegistered = true
        }.onFailure {
            Log.e(TAG, "PREF_LISTENER_FAILED package=" + packageName, it)
        }
    }

    @Synchronized
    private fun refreshConfiguration(reason: String) {
        val targets =
            preferences.getString(KEY_TARGETS, "")
                .orEmpty()
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        val nextOptions =
            preferences.getString(KEY_OPTIONS, "")
                .orEmpty()
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        val wasTracked = tracked
        tracked = packageName in targets
        options = nextOptions

        Log.i(
            TAG,
            "CONFIG package=" + packageName +
                " process=" + processName +
                " tracked=" + tracked +
                " options=" + nextOptions.sorted() +
                " reason=" + reason,
        )
        if (tracked) {
            installForCurrentOptions()
            if (!wasTracked) {
                Log.i(TAG, "TRACKING_ENABLED package=" + packageName)
            }
        }
    }

    private fun enabled(id: String): Boolean = id in options

    private fun installForCurrentOptions() {
        if (enabled("lifecycle") || enabled("method_trace")) installActivityHooks()
        if (enabled("intent") || enabled("method_trace")) installIntentHooks()
        if (enabled("webview") || enabled("method_trace")) installWebViewHooks()
        if (enabled("network") || enabled("method_trace")) installNetworkHooks()
        if (enabled("file_io") || enabled("method_trace")) installFileHooks()
        if (enabled("hook_health")) {
            Log.i(
                TAG,
                "HOOK_HEALTH package=" + packageName +
                    " process=" + processName +
                    " installed=" + installed.size,
            )
        }
    }

    private fun installActivityHooks() {
        hookMethod(
            "activity_onCreate",
            runCatching {
                Activity::class.java.getDeclaredMethod(
                    "onCreate",
                    Bundle::class.java,
                )
            }.getOrNull(),
            "lifecycle",
        ) { chain ->
            trace(
                "ACTIVITY_CREATE",
                chain.thisObject?.javaClass?.name.orEmpty(),
            )
        }
        hookMethod(
            "activity_onResume",
            runCatching {
                Activity::class.java.getDeclaredMethod("onResume")
            }.getOrNull(),
            "lifecycle",
        ) { chain ->
            trace(
                "ACTIVITY_RESUME",
                chain.thisObject?.javaClass?.name.orEmpty(),
            )
        }
        hookMethod(
            "activity_onPause",
            runCatching {
                Activity::class.java.getDeclaredMethod("onPause")
            }.getOrNull(),
            "lifecycle",
        ) { chain ->
            trace(
                "ACTIVITY_PAUSE",
                chain.thisObject?.javaClass?.name.orEmpty(),
            )
        }
    }

    private fun installIntentHooks() {
        hookMethod(
            "context_startActivity",
            runCatching {
                ContextWrapper::class.java.getDeclaredMethod(
                    "startActivity",
                    Intent::class.java,
                )
            }.getOrNull(),
            "intent",
        ) { chain ->
            val intent = chain.args.getOrNull(0) as? Intent
            trace(
                "START_ACTIVITY",
                "action=" + intent?.action +
                    " data=" + (intent?.data?.scheme ?: "-") +
                    " component=" + intent?.component,
            )
        }
    }

    private fun installWebViewHooks() {
        hookMethod(
            "webview_loadUrl",
            runCatching {
                WebView::class.java.getDeclaredMethod(
                    "loadUrl",
                    String::class.java,
                )
            }.getOrNull(),
            "webview",
        ) { chain ->
            val url = chain.args.getOrNull(0)?.toString().orEmpty()
            val safe =
                runCatching {
                    val uri = android.net.Uri.parse(url)
                    (uri.scheme ?: "") + "://" +
                        (uri.host ?: "") +
                        (uri.path ?: "")
                }.getOrDefault("<unparseable>")
            trace("WEBVIEW_LOAD_URL", safe.take(500))
        }
    }

    private fun installNetworkHooks() {
        hookMethod(
            "url_openConnection",
            runCatching {
                URL::class.java.getDeclaredMethod("openConnection")
            }.getOrNull(),
            "network",
        ) { chain ->
            val url = chain.thisObject as? URL
            trace(
                "URL_OPEN_CONNECTION",
                ((url?.protocol ?: "") + "://" +
                    (url?.host ?: "") +
                    (url?.path ?: "")).take(500),
            )
        }
    }

    private fun installFileHooks() {
        hookMethod(
            "file_input_stream_file",
            runCatching {
                FileInputStream::class.java.getDeclaredConstructor(
                    File::class.java,
                )
            }.getOrNull(),
            "file_io",
        ) { chain ->
            val file = chain.args.getOrNull(0) as? File
            trace("FILE_READ", file?.absolutePath.orEmpty().take(800))
        }
        hookMethod(
            "file_output_stream_file",
            runCatching {
                FileOutputStream::class.java.getDeclaredConstructor(
                    File::class.java,
                )
            }.getOrNull(),
            "file_io",
        ) { chain ->
            val file = chain.args.getOrNull(0) as? File
            trace("FILE_WRITE", file?.absolutePath.orEmpty().take(800))
        }
    }

    private fun hookMethod(
        id: String,
        executable: java.lang.reflect.Executable?,
        option: String,
        before: (XposedInterface.Chain) -> Unit,
    ) {
        if (executable == null || !installed.add(id)) return
        runCatching {
            executable.isAccessible = true
            hook(executable)
                .setId("ydiag-" + id)
                .intercept(
                    XposedInterface.Hooker { chain ->
                        if (!tracked) return@Hooker chain.proceed()
                        val traceEnabled =
                            enabled(option) || enabled("method_trace")
                        val healthEnabled = enabled("hook_health")
                        if (traceEnabled || healthEnabled) {
                            val count =
                                hits.getOrPut(id) {
                                    AtomicLong()
                                }.incrementAndGet()
                            if (traceEnabled) {
                                runCatching { before(chain) }
                            }
                            if (
                                healthEnabled &&
                                (count == 1L || count % 100L == 0L)
                            ) {
                                Log.i(
                                    TAG,
                                    "HOOK_HIT package=" + packageName +
                                        " process=" + processName +
                                        " id=" + id +
                                        " count=" + count,
                                )
                            }
                            if (traceEnabled && enabled("stack_trace")) {
                                val stack =
                                    Throwable().stackTrace
                                        .take(24)
                                        .joinToString(" <- ") {
                                            it.className + "#" +
                                                it.methodName + ":" +
                                                it.lineNumber
                                        }
                                Log.i(
                                    TAG,
                                    "STACK package=" + packageName +
                                        " id=" + id +
                                        " " + stack,
                                )
                            }
                        }
                        chain.proceed()
                    },
                )
        }.onFailure {
            installed.remove(id)
            Log.e(TAG, "HOOK_FAILED package=" + packageName + " id=" + id, it)
        }
    }

    private fun trace(event: String, detail: String) {
        Log.i(
            TAG,
            event + " package=" + packageName +
                " process=" + processName +
                " " + detail,
        )
    }

    companion object {
        private const val TAG = "YDiag.Hook"
        private const val GROUP = "ydiag"
        private const val KEY_TARGETS = "targets"
        private const val KEY_OPTIONS = "options"
    }
}
