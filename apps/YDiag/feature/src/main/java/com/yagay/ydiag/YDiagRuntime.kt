package com.yagay.ydiag

import android.content.Context
import android.content.Intent
import com.yagay.suite.api.FeatureServices
import com.yagay.suite.api.ManagedFeatureRuntime
import com.yagay.suite.api.XposedHostBridge
import com.yagay.suite.api.YLocale
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
    private val services = FeatureServices.of(PLUGIN_ID, TAG)
    private var runtimeJob = SupervisorJob()
    @Volatile private var appScope = CoroutineScope(runtimeJob + Dispatchers.IO)
    private val preferences by lazy { Preferences(appContext) }

    @Volatile private var enabled = true
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

    @Synchronized
    override fun enable() {
        if (!runtimeJob.isActive) {
            runtimeJob = SupervisorJob()
            appScope = CoroutineScope(runtimeJob + Dispatchers.IO)
        }
        enabled = true
        if (xposedService == null) {
            _moduleState.value = ModuleState(message = YLocale.text(R.string.ydiag_lsposed_not_connected))
        }
        services.info("managed runtime enabled")
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
        }.onFailure { services.warn("Unable to clear remote tracking state during disable", it) }

        runtimeJob.cancel()
        xposedService = null
        trackedTargets = emptySet()
        synchronized(requestedScope) { requestedScope.clear() }
        activationInFlight.clear()
        lastActivationAttempt.clear()

        runCatching {
            appContext.stopService(Intent(appContext, MonitorService::class.java))
        }.onFailure { services.warn("Unable to stop MonitorService during disable", it) }

        _moduleState.value = ModuleState(message = YLocale.text(R.string.ydiag_disabled_by_suite))
        services.info("managed runtime disabled; jobs and monitor service stopped")
    }

    override fun destroy() {
        disable()
    }

    private fun registerServiceListener() {
        when (XposedHostBridge.attachListener(appContext, PLUGIN_ID, this)) {
            XposedHostBridge.AttachResult.ATTACHED -> Unit
            XposedHostBridge.AttachResult.HOST_PRESENT_BUT_FAILED ->
                services.error("YSuite host detected but LSPosed broker attach failed")
            XposedHostBridge.AttachResult.NOT_SUITE_HOST ->
                runCatching { XposedServiceHelper.registerListener(this) }
                    .onFailure { services.warn("LSPosed service registration unavailable", it) }
        }
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
            _moduleState.value = ModuleState(message = YLocale.text(R.string.ydiag_lsposed_disconnected))
        }
    }

    fun syncDeepTracking(targets: Set<String>, options: Set<String>) {
        if (!enabled) return
        trackedTargets = targets
        appScope.launch {
            if (!enabled) return@launch
            val service = xposedService ?: run {
                _moduleState.value = ModuleState(message = YLocale.text(R.string.ydiag_root_active_lsposed_missing))
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
                services.error("Deep tracking sync failed", it)
                _moduleState.value = _moduleState.value.copy(
                    message = YLocale.text(R.string.ydiag_lsposed_sync_failed, it.javaClass.simpleName)
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
                ModuleState(message = YLocale.text(R.string.ydiag_lsposed_not_connected))
            } else runCatching { moduleSnapshot(service) }.getOrElse {
                ModuleState(connected = true, message = YLocale.text(R.string.ydiag_lsposed_read_failed))
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

        if (XposedHostBridge.isSuiteHost(appContext)) {
            val accepted = XposedHostBridge.requestScope(PLUGIN_ID, request)
            if (!accepted) {
                synchronized(requestedScope) { requestedScope.removeAll(request) }
                _moduleState.value = _moduleState.value.copy(
                    pendingScope = request,
                    message = YLocale.text(R.string.ydiag_root_active_suite_scope_failed),
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
                        message = YLocale.text(R.string.ydiag_scope_denied, message),
                    )
                }
            })
        }.onFailure {
            synchronized(requestedScope) { requestedScope.removeAll(request.toSet()) }
            if (!enabled) return@onFailure
            services.error("Scope request failed", it)
            _moduleState.value = _moduleState.value.copy(
                pendingScope = request,
                message = YLocale.text(R.string.ydiag_scope_request_failed, it.javaClass.simpleName),
            )
        }
    }

    private suspend fun ensureTargetsLoaded(
        service: XposedService,
        targets: Set<String>,
        mode: String,
    ) {
        if (!enabled || targets.isEmpty() || mode != Preferences.ACTIVATION_AUTO) return
        if (!RootShell.isAvailable()) {
            _moduleState.value = moduleSnapshot(service).copy(
                message = YLocale.text(R.string.ydiag_root_unavailable_reopen),
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
                services.info("No launch intent for $packageName; manual reopen required")
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
                    message = YLocale.text(R.string.ydiag_relaunch_failed, packageName),
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
                services.warn("Unable to relaunch $packageName", it)
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
            pending.isNotEmpty() -> YLocale.text(R.string.ydiag_waiting_scope, pending.size)
            pendingActivation.isNotEmpty() ->
                YLocale.text(R.string.ydiag_auto_reloading, pendingActivation.size)
            trackedTargets.isNotEmpty() && restartRequired.isEmpty() ->
                YLocale.text(R.string.ydiag_hooks_loaded, trackedTargets.size, trackedTargets.size)
            restartRequired.isNotEmpty() && preferences.deepActivationMode == Preferences.ACTIVATION_AUTO ->
                YLocale.text(R.string.ydiag_restart_waiting_auto, restartRequired.size)
            restartRequired.isNotEmpty() ->
                YLocale.text(R.string.ydiag_restart_required, restartRequired.size)
            else -> YLocale.text(R.string.ydiag_api_connected, service.apiVersion)
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
        private const val PLUGIN_ID = "ydiag"

        @Volatile private var instance: YDiagRuntime? = null

        @JvmStatic
        fun get(context: Context): YDiagRuntime = instance ?: synchronized(this) {
            instance ?: YDiagRuntime(context).also { instance = it }
        }
    }
}
