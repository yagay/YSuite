package com.yagay.ysuite.feature.yentrycleaner.runtime

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.os.Binder
import android.os.Process
import com.yagay.ysuite.runtime.RuntimeOwnerGate
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class YEntryComponentDiscoveryModule :
    XposedModule() {
    private data class ListResult(
        val values: List<*>,
        val rebuild: (List<*>) -> Any?,
    )

    private data class SliceAccessor(
        val getList: Method,
        val constructor: Constructor<*>,
    )

    private var moduleHostPackage: String = ""
    private lateinit var prefs:
        SharedPreferences
    private val installed =
        ConcurrentHashMap.newKeySet<String>()
    private val slices =
        ConcurrentHashMap<
            Class<*>,
            SliceAccessor
            >()

    override fun onModuleLoaded(
        param:
            XposedModuleInterface
                .ModuleLoadedParam,
    ) {
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
        prefs =
            getRemotePreferences(
                YEntryRuntimeBridge.GROUP,
            )
    }

    override fun onSystemServerStarting(
        param:
            XposedModuleInterface
                .SystemServerStartingParam,
    ) {
        if (
            !RuntimeOwnerGate.shouldRun(
                "yentrycleaner",
                moduleHostPackage,
            )
        ) return
        installPackageManager(
            param.classLoader,
        )
        installWidgets(
            param.classLoader,
        )
    }

    private fun installPackageManager(
        loader: ClassLoader,
    ) {
        listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService",
        ).forEach { name ->
            val type =
                runCatching {
                    Class.forName(
                        name,
                        false,
                        loader,
                    )
                }.getOrNull()
                    ?: return@forEach
            generateSequence(
                type as Class<*>?,
            ) {
                it.superclass
            }.flatMap {
                it.declaredMethods
                    .asSequence()
            }.filter {
                method ->
                method.parameterTypes
                    .any {
                        Intent::class.java
                            .isAssignableFrom(it)
                    } &&
                    (
                        List::class.java
                            .isAssignableFrom(
                                method.returnType,
                            ) ||
                            method.returnType.name
                                .endsWith(
                                    "ParceledListSlice",
                                )
                        ) &&
                    method.name in
                    setOf(
                        "queryIntentActivities",
                        "queryIntentActivitiesAsUser",
                        "queryIntentServices",
                        "queryIntentServicesAsUser",
                        "queryIntentReceivers",
                        "queryIntentReceiversAsUser",
                        "queryBroadcastReceivers",
                        "queryBroadcastReceiversAsUser",
                    )
            }.distinctBy {
                it.toGenericString()
            }.forEach {
                method ->
                val key =
                    "pm:" +
                        method.toGenericString()
                if (!installed.add(key)) {
                    return@forEach
                }
                runCatching {
                    method.isAccessible = true
                    hook(method)
                        .setId(
                            "yentry-discovery-pm",
                        )
                        .intercept(
                            packageQueryHook(
                                method,
                            ),
                        )
                }.onFailure {
                    installed.remove(key)
                }
            }
        }
    }

    private fun packageQueryHook(
        method: Method,
    ) =
        XposedInterface.Hooker {
            chain ->
            val intent =
                chain.args
                    .firstOrNull {
                        it is Intent
                    } as? Intent
                    ?: return@Hooker chain.proceed()
            val action =
                (intent.selector ?: intent)
                    .action
                    ?: return@Hooker chain.proceed()
            val valid =
                when {
                    action ==
                        "android.service.quicksettings.action.QS_TILE" &&
                        "Service" in
                        method.name ->
                        true
                    action ==
                        "android.intent.action.CREATE_SHORTCUT" &&
                        "Activit" in
                        method.name ->
                        true
                    action ==
                        "android.appwidget.action.APPWIDGET_UPDATE" &&
                        (
                            "Receiver" in
                                method.name ||
                                "Broadcast" in
                                method.name
                            ) ->
                        true
                    else -> false
                }
            if (!valid) {
                return@Hooker chain.proceed()
            }
            if (managerCaller()) {
                return@Hooker chain.proceed()
            }
            val original =
                chain.proceed()
            filterResolve(
                original,
                chain.args
                    .filterIsInstance<Int>()
                    .lastOrNull(),
            )
        }

    private fun filterResolve(
        original: Any?,
        fallbackUser: Int?,
    ): Any? {
        val protected =
            protectedComponents()
        if (protected.isEmpty()) {
            return original
        }
        val result =
            extract(original)
                ?: return original
        val filtered =
            result.values.filter {
                value ->
                val info =
                    value as? ResolveInfo
                        ?: return@filter true
                val componentInfo =
                    info.activityInfo
                        ?: info.serviceInfo
                        ?: return@filter true
                val user =
                    componentInfo
                        .applicationInfo?.uid
                        ?.takeIf { it >= 0 }
                        ?.div(100_000)
                        ?: fallbackUser
                !isProtected(
                    protected,
                    user,
                    ComponentName(
                        componentInfo
                            .packageName,
                        componentInfo.name,
                    ),
                )
            }
        return if (
            filtered.size ==
            result.values.size
        ) {
            original
        } else {
            runCatching {
                result.rebuild(
                    filtered,
                )
            }.getOrDefault(original)
        }
    }

    private fun installWidgets(
        loader: ClassLoader,
    ) {
        val type =
            runCatching {
                Class.forName(
                    "com.android.server.appwidget.AppWidgetServiceImpl",
                    false,
                    loader,
                )
            }.getOrNull()
                ?: return
        generateSequence(
            type as Class<*>?,
        ) {
            it.superclass
        }.flatMap {
            it.declaredMethods.asSequence()
        }.filter {
            it.name ==
                "getInstalledProvidersForProfile" &&
                (
                    List::class.java
                        .isAssignableFrom(
                            it.returnType,
                        ) ||
                        it.returnType.name
                            .endsWith(
                                "ParceledListSlice",
                            )
                    )
        }.distinctBy {
            it.toGenericString()
        }.forEach {
            method ->
            val key =
                "widget:" +
                    method.toGenericString()
            if (!installed.add(key)) {
                return@forEach
            }
            runCatching {
                method.isAccessible = true
                hook(method)
                    .setId(
                        "yentry-discovery-widget",
                    )
                    .intercept(
                        XposedInterface.Hooker {
                            chain ->
                            if (managerCaller()) {
                                return@Hooker chain.proceed()
                            }
                            val original =
                                chain.proceed()
                            val protected =
                                protectedComponents()
                            if (
                                protected.isEmpty()
                            ) {
                                return@Hooker original
                            }
                            val result =
                                extract(original)
                                    ?: return@Hooker original
                            val user =
                                chain.args
                                    .filterIsInstance<Int>()
                                    .lastOrNull()
                            val filtered =
                                result.values.filter {
                                    value ->
                                    val info =
                                        value as?
                                            AppWidgetProviderInfo
                                            ?: return@filter true
                                    val provider =
                                        info.provider
                                    !isProtected(
                                        protected,
                                        user,
                                        provider,
                                    )
                                }
                            if (
                                filtered.size ==
                                result.values.size
                            ) {
                                original
                            } else {
                                runCatching {
                                    result.rebuild(
                                        filtered,
                                    )
                                }.getOrDefault(
                                    original,
                                )
                            }
                        },
                    )
            }.onFailure {
                installed.remove(key)
            }
        }
    }

    private fun managerCaller(): Boolean {
        val appId =
            prefs.getString(
                YEntryRuntimeBridge
                    .KEY_MANAGER_APP_ID,
                "-1",
            )?.toIntOrNull()
                ?: -1
        return appId >= 0 &&
            Binder.getCallingUid() %
            100_000 ==
            appId
    }

    private fun protectedComponents():
        Set<String> =
        prefs.getString(
            YEntryRuntimeBridge
                .KEY_DISABLED_COMPONENTS,
            "",
        ).orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()

    private fun isProtected(
        values: Set<String>,
        user: Int?,
        component: ComponentName,
    ): Boolean {
        val exact =
            user?.let {
                YEntryRuntimeBridge
                    .componentKey(
                        it,
                        component.packageName,
                        component.className,
                    )
            }
        if (
            exact != null &&
            exact in values
        ) return true
        val suffix =
            "|" +
                component.packageName +
                "|" +
                component.className
        return values.any {
            it.endsWith(suffix)
        }
    }

    private fun extract(
        original: Any?,
    ): ListResult? =
        when {
            original is List<*> ->
                ListResult(original) {
                    it
                }
            original == null -> null
            original.javaClass.name
                .endsWith(
                    "ParceledListSlice",
                ) -> {
                val accessor =
                    runCatching {
                        slices.computeIfAbsent(
                            original.javaClass,
                        ) {
                            type ->
                            val getList =
                                type.methods
                                    .first {
                                        it.name ==
                                            "getList" &&
                                            it.parameterCount ==
                                            0
                                    }.apply {
                                        isAccessible =
                                            true
                                    }
                            val constructor =
                                type.declaredConstructors
                                    .first {
                                        it.parameterTypes.size ==
                                            1 &&
                                            List::class.java
                                                .isAssignableFrom(
                                                    it.parameterTypes[
                                                        0
                                                    ],
                                                )
                                    }.apply {
                                        isAccessible =
                                            true
                                    }
                            SliceAccessor(
                                getList,
                                constructor,
                            )
                        }
                    }.getOrNull()
                        ?: return null
                val values =
                    runCatching {
                        accessor.getList
                            .invoke(original)
                            as? List<*>
                    }.getOrNull()
                        ?: return null
                ListResult(values) {
                    list ->
                    accessor.constructor
                        .newInstance(list)
                }
            }
            else -> null
        }
}
