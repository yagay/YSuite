package com.yagay.ydiag

import android.content.Context
import android.content.Intent
import android.util.Log
import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.ManagedFeatureRuntime
import com.yagay.ydiag.data.Preferences
import com.yagay.ydiag.root.RootShell
import com.yagay.ydiag.service.MonitorService
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Host-neutral runtime used by both the standalone APK and YSuite.
 * Keep Android Application subclasses thin so this feature can be embedded safely.
 */
class YDiagRuntime private constructor(context: Context) :
    XposedServiceHelper.OnServiceListener,
    ManagedFeatureRuntime {

    private val appContext = context.applicationContext
    private var runtimeJob = SupervisorJob()
    @Volatile private var appScope = CoroutineScope(runtimeJob + Dispatchers.IO)
    private val preferences by lazy { Preferences(appContext) }

    @Volatile private var enabled = true
    @Volatile private var host: FeatureHost? = null
    @Volatile private var xposedService: XposedService? = null
    @Volatile private var trackedTargets: Set<String> = emptySet()

    private val requestedScope = linkedSetOf<String>()
    private val activationInFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastActivationAttempt = ConcurrentHashMap<String, Long>()

    private val _moduleState = MutableStateFlow(ModuleState())
    val moduleState: StateFlow<ModuleState> = _moduleState

    init {
        registerServiceListener()
    }

    override fun attach(host: FeatureHost) {
        this.host = host
    }

    @Synchronized
    override fun enable() {
        if (!runtimeJob.isActive) {
            runtimeJob = SupervisorJob()
            appScope = CoroutineScope(runtimeJob + Dispatchers.IO)
        }
        enabled = true
        if (xposedService == null) {
            _moduleState.value = ModuleState(message = "LSPosed 未连接")
        }
        Log.i(TAG, "managed runtime enabled")
    }

    @Synchronized
    override fun disable() {
        if (!enabled && !runtimeJob.isActive) return
        enabled = false

        val service = xposedService
        runCatching {
            service?.getRemotePreferences(PREFS)
                ?.edit()
                ?.putStringSet(KEY_TARGETS, emptySet())
                ?.putStringSet(KEY_OPTIONS, emptySet())
                ?.putLong(KEY_REVISION, System.currentTimeMillis())
                ?.commit()
        }.onFailure { Log.w(TAG, "Unable to clear remote tracking state during disable", it) }

        runtimeJob.cancel()
        xposedService = null
        trackedTargets = emptySet()
        synchronized(requestedScope) { requestedScope.clear() }
        activationInFlight.clear()
        lastActivationAttempt.clear()

        runCatching {
            appContext.stopService(Intent(appContext, MonitorService::class.java))
        }.onFailure { Log.w(TAG, "Unable to stop MonitorService during disable", it) }

        _moduleState.value = ModuleState(message = "YDiag 已由 YSuite 停用")
        Log.i(TAG, "managed runtime disabled; jobs and monitor service stopped")
    }

    override fun destroy() {
        disable()
        host = null
    }

    private fun registerServiceListener() {
        if (appContext.packageName == SUITE_PACKAGE) {
            val attached = runCatching {
                val broker = Class.forName(SUITE_BROKER, false, javaClass.classLoader)
                val method = broker.getMethod("attachFromPlugin", String::class.java, Any::class.java)
                method.invoke(null, PLUGIN_ID, this) as? Boolean == true
            }.getOrElse {
                Log.e(TAG, "YSuite LSPosed broker registration failed", it)
                false
            }
            if (!attached) {
                Log.e(TAG, "YSuite host detected but broker unavailable; standalone listener is disabled")
            }
            return
        }

        runCatching { XposedServiceHelper.registerListener(this) }
            .onFailure { Log.w(TAG, "LSPosed service registration unavailable", it) }
    }

    override fun onServiceBind(service: XposedService) {
        if (!enabled) return
        xposedService = service
        refreshModuleState()
        appScope.launch {
            delay(150)
            if (!enabled) return@launch
            val granted = runCatching { service.scope.toSet() }.getOrDefault(emptySet())
            val missingDefault = DEFAULT_SCOPE - granted
            if (missingDefault.isNotEmpty()) {
                requestMissingScope(
                    service = service,
                    missing = missingDefault,
                    activationTargets = emptySet(),
                    activationMode = Preferences.ACTIVATION_MANUAL,
                )
            }
        }
    }

    override fun onServiceDied(service: XposedService) {
        if (xposedService === service) xposedService = null
        activationInFlight.clear()
        if (enabled) {
            _moduleState.value = ModuleState(message = "LSPosed 连接已断开")
        }
    }

    fun syncDeepTracking(targets: Set<String>, options: Set<String>) {
        if (!enabled) return
        trackedTargets = targets
        appScope.launch {
            if (!enabled) return@launch
            val service = xposedService ?: run {
                _moduleState.value = ModuleState(message = "Root 监控正常；LSPosed 深度追踪未连接")
                return@launch
            }
            runCatching {
                service.getRemotePreferences(PREFS)
                    .edit()
                    .putStringSet(KEY_TARGETS, targets)
                    .putStringSet(KEY_OPTIONS, options)
                    .putLong(KEY_REVISION, System.currentTimeMillis())
                    .commit()

                val mode = preferences.deepActivationMode
                val granted = service.scope.toSet()
                val dynamicTargets = if (mode == Preferences.ACTIVATION_ROOT_ONLY) emptySet() else targets
                val desiredScope = DEFAULT_SCOPE + dynamicTargets
                val missing = desiredScope - granted

                _moduleState.value = moduleSnapshot(service, pending = missing)
                ensureTargetsLoaded(service, targets.intersect(granted), mode)

                if (missing.isNotEmpty()) {
                    requestMissingScope(
                        service = service,
                        missing = missing,
                        activationTargets = targets,
                        activationMode = mode,
                    )
                } else {
                    refreshModuleState()
                }
            }.onFailure {
                Log.e(TAG, "Deep tracking sync failed", it)
                _moduleState.value = _moduleState.value.copy(
                    message = "LSPosed 配置同步失败：${it.javaClass.simpleName}"
                )
            }
        }
    }

    fun refreshModuleState() {
        if (!enabled) return
        appScope.launch {
            if (!enabled) return@launch
            val service = xposedService
            _moduleState.value = if (service == null) {
                ModuleState(message = "LSPosed 未连接")
            } else runCatching { moduleSnapshot(service) }.getOrElse {
                ModuleState(connected = true, message = "读取 LSPosed 状态失败")
            }
        }
    }

    private fun requestMissingScope(
        service: XposedService,
        missing: Set<String>,
        activationTargets: Set<String>,
        activationMode: String,
    ) {
        if (!enabled) return
        val request = synchronized(requestedScope) {
            missing.filterNot { it in requestedScope }.also { requestedScope += it }.toSet()
        }
        if (request.isEmpty()) return

        _moduleState.value = moduleSnapshot(service, pending = request)

        if (appContext.packageName == SUITE_PACKAGE) {
            val accepted = requestScopeThroughHost(request)
            if (!accepted) {
                synchronized(requestedScope) { requestedScope.removeAll(request) }
                _moduleState.value = _moduleState.value.copy(
                    pendingScope = request,
                    message = "Root 日志已生效；YSuite Scope 请求失败",
                )
                return
            }
            appScope.launch {
                delay(500)
                if (!enabled) return@launch
                synchronized(requestedScope) { requestedScope.removeAll(request) }
                refreshModuleState()
                val granted = runCatching { service.scope.toSet() }.getOrDefault(emptySet())
                ensureTargetsLoaded(service, activationTargets.intersect(granted), activationMode)
            }
            return
        }

        runCatching {
            service.requestScope(request.toList(), object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    synchronized(requestedScope) { requestedScope.removeAll(request.toSet()) }
                    if (!enabled) return
                    appScope.launch {
                        delay(250)
                        if (!enabled) return@launch
                        refreshModuleState()
                        ensureTargetsLoaded(service, activationTargets, activationMode)
                    }
                }

                override fun onScopeRequestFailed(message: String) {
                    synchronized(requestedScope) { requestedScope.removeAll(request.toSet()) }
                    if (!enabled) return
                    _moduleState.value = _moduleState.value.copy(
                        pendingScope = request,
                        message = "Root 日志已生效；深度 Scope 未授权：$message",
                    )
                }
            })
        }.onFailure {
            synchronized(requestedScope) { requestedScope.removeAll(request.toSet()) }
            if (!enabled) return@onFailure
            Log.e(TAG, "Scope request failed", it)
            _moduleState.value = _moduleState.value.copy(
                pendingScope = request,
                message = "Root 日志已生效；Scope 请求失败：${it.javaClass.simpleName}",
            )
        }
    }

    private fun requestScopeThroughHost(request: Set<String>): Boolean = runCatching {
        val broker = Class.forName(SUITE_BROKER, false, javaClass.classLoader)
        val method = broker.methods.firstOrNull {
            it.name == "requestScopeFromPlugin" && it.parameterTypes.size == 2
        } ?: error("YSuite scope broker method missing")
        method.invoke(null, PLUGIN_ID, request.toTypedArray()) as? Boolean == true
    }.getOrElse {
        Log.e(TAG, "YSuite scope request failed", it)
        false
    }

    private suspend fun ensureTargetsLoaded(
        service: XposedService,
        targets: Set<String>,
        mode: String,
    ) {
        if (!enabled || targets.isEmpty() || mode != Preferences.ACTIVATION_AUTO) return
        if (!RootShell.isAvailable()) {
            _moduleState.value = moduleSnapshot(service).copy(
                message = "深度 Scope 已授权；Root 不可用，请手动重新打开目标 App",
            )
            return
        }

        val granted = runCatching { service.scope.toSet() }.getOrDefault(emptySet())
        val loaded = loadedPackages(service)
        val now = System.currentTimeMillis()

        val candidates = targets.filter { packageName ->
            packageName in granted &&
                packageName !in loaded &&
                packageName !in NEVER_AUTO_RESTART &&
                PACKAGE_NAME.matches(packageName) &&
                now - (lastActivationAttempt[packageName] ?: 0L) >= ACTIVATION_COOLDOWN_MS
        }

        for (packageName in candidates) {
            if (!enabled) return
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                Log.i(TAG, "No launch intent for $packageName; manual reopen required")
                continue
            }

            lastActivationAttempt[packageName] = System.currentTimeMillis()
            activationInFlight += packageName
            _moduleState.value = moduleSnapshot(service)

            val stopped = runCatching {
                RootShell.exec("am force-stop $packageName", 6).code == 0
            }.getOrDefault(false)

            if (!stopped) {
                activationInFlight -= packageName
                _moduleState.value = moduleSnapshot(service).copy(
                    message = "无法自动重启 $packageName，请手动重新打开",
                )
                continue
            }

            delay(350)
            if (!enabled) return
            runCatching {
                appContext.startActivity(
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                )
            }.onFailure {
                Log.w(TAG, "Unable to relaunch $packageName", it)
            }

            delay(1600)
            if (!enabled) return
            activationInFlight -= packageName
            _moduleState.value = moduleSnapshot(service)
        }
    }

    private fun moduleSnapshot(
        service: XposedService,
        pending: Set<String> = synchronized(requestedScope) { requestedScope.toSet() },
    ): ModuleState {
        val targets = if (service.apiVersion >= 102) {
            service.runningTargets.map { target: HookedTarget ->
                "${target.processName} · ${target.state.name} · v${target.loadedVersionCode}"
            }
        } else emptyList()

        val loaded = loadedPackages(service)
        val granted = service.scope.toSet()
        val pendingActivation = activationInFlight.toSet()
        val restartRequired = trackedTargets.filterTo(linkedSetOf()) {
            it in granted && it !in loaded && it !in pendingActivation
        }

        val message = when {
            pending.isNotEmpty() -> "Root 日志已生效；等待授权 ${pending.size} 个 Hook Scope"
            pendingActivation.isNotEmpty() ->
                "正在自动重新加载 ${pendingActivation.size} 个目标 App，使 Hook 立即生效"
            trackedTargets.isNotEmpty() && restartRequired.isEmpty() ->
                "深度 Hook 已加载 ${trackedTargets.size}/${trackedTargets.size}"
            restartRequired.isNotEmpty() && preferences.deepActivationMode == Preferences.ACTIVATION_AUTO ->
                "Scope 已授权；${restartRequired.size} 个目标等待自动/手动重新打开"
            restartRequired.isNotEmpty() ->
                "Scope 已授权；${restartRequired.size} 个目标需重新打开后启用深度 Hook"
            else -> "LSPosed API ${service.apiVersion} 已连接"
        }

        return ModuleState(
            connected = true,
            apiVersion = service.apiVersion,
            scope = granted,
            runningTargets = targets,
            loadedPackages = loaded,
            pendingScope = pending,
            activationPending = pendingActivation,
            restartRequired = restartRequired,
            systemScoped = "system" in granted,
            systemLoaded = "system" in loaded,
            message = message,
        )
    }

    private fun loadedPackages(service: XposedService): Set<String> {
        if (service.apiVersion < 102) return emptySet()
        return service.runningTargets.mapTo(linkedSetOf()) { target ->
            if (target.processName == "system") "system" else target.processName.substringBefore(':')
        }
    }

    companion object {
        const val PREFS = "ydiag_tracking"
        const val KEY_TARGETS = "targets"
        const val KEY_OPTIONS = "options"
        const val KEY_REVISION = "revision"

        val DEFAULT_SCOPE: Set<String> = setOf("system")

        private val NEVER_AUTO_RESTART = setOf(
            "android",
            "system",
            "com.android.systemui",
            "com.android.phone",
        )
        private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
        private const val ACTIVATION_COOLDOWN_MS = 15_000L
        private const val TAG = "YDiag.Runtime"
        private const val SUITE_PACKAGE = "com.yagay.YSuite"
        private const val SUITE_BROKER = "com.yagay.suite.core.SuiteXposedServiceBroker"
        private const val PLUGIN_ID = "ydiag"

        @Volatile private var instance: YDiagRuntime? = null

        @JvmStatic
        fun get(context: Context): YDiagRuntime = instance ?: synchronized(this) {
            instance ?: YDiagRuntime(context).also { instance = it }
        }
    }
}
