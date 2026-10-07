package com.yagay.ysuite.feature.yentrycleaner.runtime

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class YEntryComponentGuardModule :
    XposedModule() {
    private lateinit var prefs: SharedPreferences
    private val installed =
        ConcurrentHashMap.newKeySet<String>()

    override fun onModuleLoaded(
        param: XposedModuleInterface.ModuleLoadedParam,
    ) {
        prefs =
            getRemotePreferences(
                YEntryRuntimeBridge.GROUP,
            )
    }

    override fun onSystemServerStarting(
        param: XposedModuleInterface.SystemServerStartingParam,
    ) {
        install(param.classLoader)
    }

    private fun install(loader: ClassLoader) {
        val classes =
            listOf(
                "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
                "com.android.server.pm.IPackageManagerImpl",
                "com.android.server.pm.PackageManagerService",
            )
        for (name in classes) {
            val type =
                runCatching {
                    Class.forName(
                        name,
                        false,
                        loader,
                    )
                }.getOrNull() ?: continue
            generateSequence(type as Class<*>?) {
                it.superclass
            }.flatMap {
                it.declaredMethods.asSequence()
            }.filter {
                it.name ==
                    "setComponentEnabledSetting" ||
                    it.name ==
                    "setComponentEnabledSettings"
            }.distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installed.add(key)) {
                        return@forEach
                    }
                    runCatching {
                        method.isAccessible = true
                        hook(method)
                            .setId("yentry-component-guard")
                            .intercept(
                                if (
                                    method.name ==
                                    "setComponentEnabledSetting"
                                ) {
                                    single(method)
                                } else {
                                    batch(method)
                                },
                            )
                    }.onFailure {
                        installed.remove(key)
                    }
                }
        }
    }

    private fun single(method: Method) =
        XposedInterface.Hooker { chain ->
            val caller = Binder.getCallingUid()
            if (caller < Process.FIRST_APPLICATION_UID) {
                return@Hooker chain.proceed()
            }
            val componentIndex =
                method.parameterTypes
                    .indexOfFirst {
                        ComponentName::class.java
                            .isAssignableFrom(it)
                    }
            if (componentIndex < 0) {
                return@Hooker chain.proceed()
            }
            val stateIndex = componentIndex + 1
            val userIndex =
                method.parameterTypes.indices
                    .lastOrNull {
                        method.parameterTypes[it] ==
                            Int::class.javaPrimitiveType
                    } ?: return@Hooker chain.proceed()
            val component =
                chain.args.getOrNull(
                    componentIndex,
                ) as? ComponentName
                    ?: return@Hooker chain.proceed()
            val state =
                chain.args.getOrNull(stateIndex)
                    as? Int
                    ?: return@Hooker chain.proceed()
            val user =
                chain.args.getOrNull(userIndex)
                    as? Int
                    ?: return@Hooker chain.proceed()
            if (
                shouldBlock(
                    user,
                    component,
                    state,
                )
            ) {
                record(
                    "REENABLE_BLOCKED " +
                        component.flattenToShortString(),
                )
                null
            } else {
                chain.proceed()
            }
        }

    private fun batch(method: Method) =
        XposedInterface.Hooker { chain ->
            val caller = Binder.getCallingUid()
            if (caller < Process.FIRST_APPLICATION_UID) {
                return@Hooker chain.proceed()
            }
            val listIndex =
                method.parameterTypes
                    .indexOfFirst {
                        List::class.java.isAssignableFrom(
                            it,
                        )
                    }
            if (listIndex < 0) {
                return@Hooker chain.proceed()
            }
            val userIndex =
                method.parameterTypes.indices
                    .lastOrNull {
                        method.parameterTypes[it] ==
                            Int::class.javaPrimitiveType
                    } ?: return@Hooker chain.proceed()
            val user =
                chain.args.getOrNull(userIndex)
                    as? Int
                    ?: return@Hooker chain.proceed()
            val original =
                chain.args.getOrNull(listIndex)
                    as? List<*>
                    ?: return@Hooker chain.proceed()
            val filtered =
                original.filter { item ->
                    val component =
                        item?.let {
                            runCatching {
                                it.javaClass
                                    .getMethod(
                                        "getComponentName",
                                    )
                                    .invoke(it)
                                    as? ComponentName
                            }.getOrNull()
                        }
                    val state =
                        item?.let {
                            runCatching {
                                it.javaClass
                                    .getMethod(
                                        "getEnabledState",
                                    )
                                    .invoke(it)
                                    as? Int
                            }.getOrNull()
                        }
                    !(
                        component != null &&
                            state != null &&
                            shouldBlock(
                                user,
                                component,
                                state,
                            )
                        )
                }
            if (filtered.size == original.size) {
                chain.proceed()
            } else if (filtered.isEmpty()) {
                null
            } else {
                val args =
                    chain.args.toTypedArray()
                args[listIndex] = filtered
                chain.proceed(args)
            }
        }

    private fun shouldBlock(
        user: Int,
        component: ComponentName,
        state: Int,
    ): Boolean {
        if (
            state !=
            PackageManager
                .COMPONENT_ENABLED_STATE_DEFAULT &&
            state !=
            PackageManager
                .COMPONENT_ENABLED_STATE_ENABLED
        ) {
            return false
        }
        val key =
            YEntryRuntimeBridge.componentKey(
                user,
                component.packageName,
                component.className,
            )
        return key in disabled()
    }

    private fun disabled(): Set<String> =
        prefs.getString(
            YEntryRuntimeBridge
                .KEY_DISABLED_COMPONENTS,
            "",
        ).orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private fun record(message: String) {
        runCatching {
            Log.i(
                "YEntryCleaner.ComponentGuard",
                message,
            )
        }
        runCatching {
            log(
                Log.INFO,
                "YEntryCleaner.ComponentGuard",
                message,
            )
        }
    }
}
