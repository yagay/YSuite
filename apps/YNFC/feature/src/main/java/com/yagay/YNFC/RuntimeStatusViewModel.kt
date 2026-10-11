package com.yagay.YNFC

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yagay.YNFC.system.NfcSystemService
import com.yagay.YNFC.system.RootShell
import com.yagay.suite.api.FeatureHookEvidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Provider changes are the single normal-runtime source of truth for UI status. */
class RuntimeStatusViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RuntimeStatusRepository(
        application,
        NfcSystemService(RootShell(application))
    )
    private val _status = MutableStateFlow(RuntimeStatus())
    val status: StateFlow<RuntimeStatus> = _status.asStateFlow()
    @Volatile private var observedNfcPid: Int? = null

    private fun publishEvidence(status: RuntimeStatus) {
        val verified = status.currentPid > 0 && status.scopeOk &&
            status.hookInstalled && status.hookPid == status.currentPid &&
            status.hookBuild == BuildConfig.HOOK_BUILD
        FeatureHookEvidence.publish(
            featureId = "ynfc",
            process = "com.android.nfc",
            pid = status.hookPid,
            build = status.hookBuild.toLong(),
            installed = verified,
            detail = RuntimeText.hookDiagnosis(status, BuildConfig.HOOK_BUILD),
        )
    }

    init {
        viewModelScope.launch {
            repository.observeChanges().collectLatest {
                _status.value = withContext(Dispatchers.IO) {
                    repository.decode(repository.readProviderMap(), observedNfcPid)
                }.also(::publishEvidence)
            }
        }
        viewModelScope.launch {
            // Provider PID fields are all zero if the module never injected.
            // Recheck the actual NFC PID separately without running Root on
            // every high-frequency provider change or on the Compose thread.
            while (isActive) {
                val current = withContext(Dispatchers.IO) {
                    repository.currentNfcPid()
                }
                observedNfcPid = current
                _status.value = withContext(Dispatchers.IO) {
                    repository.decode(repository.readProviderMap(), current)
                }.also(::publishEvidence)
                delay(15_000L)
            }
        }
    }
}
