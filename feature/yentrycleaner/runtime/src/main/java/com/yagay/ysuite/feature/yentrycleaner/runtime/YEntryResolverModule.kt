package com.yagay.ysuite.feature.yentrycleaner.runtime

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.os.Process
import android.util.Log
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryIntentRouting
import com.yagay.ysuite.feature.yentrycleaner.api.YEntrySurface
import com.yagay.ysuite.runtime.RuntimeOwnerGate
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class YEntryResolverModule : XposedModule() {
    private data class ListResult(
        val values: List<*>,
        val rebuild: (List<*>) -> Any?,
    )
    private data class SliceAccessor(
        val getList: Method,
        val constructor: Constructor<*>,
    )

    private val installed = ConcurrentHashMap.newKeySet<String>()
    private val sliceAccessors = ConcurrentHashMap<Class<*>, SliceAccessor>()
    private lateinit var prefs: SharedPreferences
    @Volatile private var processName: String = ""
    private var moduleHostPackage: String = ""

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
            "yentrycleaner",
            moduleHostPackage,
        )
        prefs = getRemotePreferences(YEntryRuntimeBridge.GROUP)
        record("MODULE_LOADED")
    }

    override fun onSystemServerStarting(
        param: XposedModuleInterface.SystemServerStartingParam,
    ) {
        if (
            !RuntimeOwnerGate.shouldRun(
                "yentrycleaner",
                moduleHostPackage,
            )
        ) return
        installQueryHooks(param.classLoader)
    }

    override fun onPackageReady(
        param: XposedModuleInterface.PackageReadyParam,
    ) {
        if (
            !RuntimeOwnerGate.shouldRun(
                "yentrycleaner",
                moduleHostPackage,
            )
        ) return
        if (
            param.packageName == "android" ||
            param.packageName == "com.android.intentresolver"
        ) {
            installQueryHooks(param.classLoader)
        }
    }

    private fun installQueryHooks(loader: ClassLoader) {
        val classes =
            listOf(
                "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
                "com.android.server.pm.IPackageManagerImpl",
                "com.android.server.pm.PackageManagerService",
                "com.android.server.pm.ComputerEngine",
                "com.android.server.pm.ResolveIntentHelper",
                "android.app.ApplicationPackageManager",
            )
        for (name in classes) {
            val type =
                runCatching {
                    Class.forName(name, false, loader)
                }.getOrNull() ?: continue
            generateSequence(type as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter {
                    it.name.contains("queryIntentActivities") &&
                        (
                            List::class.java.isAssignableFrom(it.returnType) ||
                            it.returnType.name.endsWith("ParceledListSlice")
                            )
                }
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installed.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method)
                            .setId("yentry-resolver")
                            .intercept(queryHook())
                    }.onFailure {
                        installed.remove(key)
                    }
                }
        }
        record("QUERY_HOOKS=" + installed.size)
    }

    private fun queryHook() =
        XposedInterface.Hooker { chain ->
            val intent =
                chain.args.firstOrNull {
                    it is Intent
                } as? Intent
                ?: return@Hooker chain.proceed()
            val surface = surface(intent)
                ?: return@Hooker chain.proceed()
            val original = chain.proceed()
            val result = extract(original)
                ?: return@Hooker original
            val qualifier = qualifier(surface, intent)
            val hidden = stringSet(YEntryRuntimeBridge.KEY_HIDDEN_RULES)
            val mode =
                prefs.getString(
                    YEntryRuntimeBridge.KEY_MODE,
                    "HIDE_SELECTED",
                ) ?: "HIDE_SELECTED"
            val selectedForSurface =
                hidden.filter {
                    it.startsWith(
                        surface + "|" + qualifier + "|",
                    ) ||
                        it.startsWith(
                            surface + "|*|",
                        )
                }.toSet()
            var values =
                result.values.filter { item ->
                    val info = item as? ResolveInfo
                        ?: return@filter true
                    val activity = info.activityInfo
                        ?: return@filter true
                    val exact =
                        YEntryRuntimeBridge.ruleKey(
                            surface,
                            qualifier,
                            activity.packageName,
                            activity.name,
                        )
                    val wildcard =
                        YEntryRuntimeBridge.ruleKey(
                            surface,
                            "*",
                            activity.packageName,
                            activity.name,
                        )
                    val selected =
                        exact in hidden ||
                            wildcard in hidden
                    when (mode) {
                        "SHOW_ALL" -> true
                        "SHOW_SELECTED" ->
                            selectedForSurface.isEmpty() ||
                                selected
                        else -> !selected
                    }
                }

            val priority =
                priorityFor(surface, qualifier)
            if (priority.isNotEmpty()) {
                val rank = priority.withIndex()
                    .associate {
                        it.value to it.index
                    }
                values =
                    values.withIndex()
                        .sortedWith(
                            compareBy<IndexedValue<*>> {
                                val ri = it.value as? ResolveInfo
                                val ai = ri?.activityInfo
                                if (ai == null) {
                                    Int.MAX_VALUE
                                } else {
                                    val exact =
                                        YEntryRuntimeBridge.ruleKey(
                                            surface,
                                            qualifier,
                                            ai.packageName,
                                            ai.name,
                                        )
                                    val wildcard =
                                        YEntryRuntimeBridge.ruleKey(
                                            surface,
                                            "*",
                                            ai.packageName,
                                            ai.name,
                                        )
                                    rank[exact] ?:
                                        rank[wildcard] ?:
                                        Int.MAX_VALUE
                                }
                            }.thenBy { it.index },
                        )
                        .map { it.value }
            }
            if (diagnostic()) {
                record(
                    "FILTER surface=" +
                        surface +
                        " qualifier=" +
                        qualifier +
                        " before=" +
                        result.values.size +
                        " after=" +
                        values.size,
                )
            }
            runCatching {
                result.rebuild(values)
            }.getOrDefault(original)
        }

    private fun surface(intent: Intent): String? =
        YEntryIntentRouting.surface(
            action = intent.action,
            mimeType = intent.type,
            scheme = intent.data?.scheme,
        )?.name

    private fun qualifier(
        surface: String,
        intent: Intent,
    ): String =
        YEntryIntentRouting.qualifier(
            surface = YEntrySurface.valueOf(surface),
            mimeType = intent.type,
            host = intent.data?.host,
        )

    private fun stringSet(key: String): Set<String> =
        prefs.getString(key, "")
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private fun priorityFor(
        surface: String,
        qualifier: String,
    ): List<String> {
        val exactPrefix =
            surface + "|" + qualifier + "	"
        val wildcardPrefix =
            surface + "|*	"
        val lines =
            prefs.getString(
                YEntryRuntimeBridge.KEY_PRIORITIES,
                "",
            ).orEmpty().lineSequence().toList()
        val line =
            lines.firstOrNull {
                it.startsWith(exactPrefix)
            } ?: lines.firstOrNull {
                it.startsWith(wildcardPrefix)
            } ?: return emptyList()
        return line.substringAfter('	')
            .split('>')
            .map(String::trim)
            .filter(String::isNotEmpty)
    }

    private fun diagnostic(): Boolean =
        prefs.getString(
            YEntryRuntimeBridge.KEY_DIAGNOSTIC,
            "false",
        ).toBoolean()

    private fun extract(original: Any?): ListResult? =
        when {
            original is List<*> ->
                ListResult(original) { it }
            original == null -> null
            original.javaClass.name
                .endsWith("ParceledListSlice") ->
                extractSlice(original)
            else -> null
        }

    private fun extractSlice(
        original: Any,
    ): ListResult? {
        val accessor =
            runCatching {
                sliceAccessors.computeIfAbsent(
                    original.javaClass,
                ) { type ->
                    val getter =
                        type.methods.first {
                            it.name == "getList" &&
                                it.parameterCount == 0
                        }.apply {
                            isAccessible = true
                        }
                    val constructor =
                        type.declaredConstructors.first {
                            it.parameterTypes.size == 1 &&
                                List::class.java.isAssignableFrom(
                                    it.parameterTypes[0],
                                )
                        }.apply {
                            isAccessible = true
                        }
                    SliceAccessor(
                        getter,
                        constructor,
                    )
                }
            }.getOrNull() ?: return null
        val values =
            runCatching {
                accessor.getList.invoke(original)
                    as? List<*>
            }.getOrNull() ?: return null
        return ListResult(values) {
            accessor.constructor.newInstance(it)
        }
    }

    private fun record(message: String) {
        val line =
            "pid=" +
                Process.myPid() +
                " process=" +
                processName +
                " " +
                message
        runCatching {
            Log.i("YEntryCleaner", line)
        }
        runCatching {
            log(
                Log.INFO,
                "YEntryCleaner",
                line,
            )
        }
    }
}
