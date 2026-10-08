package com.yagay.ysuite.feature.ynfc
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.ynfc.runtime.YNfcCard
import com.yagay.ysuite.platform.api.CapabilityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
data class YNfcUiState(
    val cards: List<YNfcCard> = emptyList(),
    val runtime: YNfcRuntimeSnapshot = YNfcRuntimeSnapshot(),
    val rootStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val hookStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val busy: Boolean = false,
    val statusToken: String? = null,
    val diagnostics: String = "",
    val exportUri: String? = null,
)
class YNfcViewModel(private val environment: YNfcEnvironment) : ViewModel() {
    private val repository = YNfcRepository(environment.applicationContext, environment.rootGateway, environment.hookGateway)
    private val mutableState = MutableStateFlow(YNfcUiState(cards = repository.cards()))
    val state: StateFlow<YNfcUiState> = mutableState.asStateFlow()
    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(2_000L)
            }
        }
    }
    fun saveCard(card: YNfcCard) {
        val cards = (mutableState.value.cards.filterNot {
            it.uid.equals(card.uid, true)
        } + card).sortedBy { it.name.lowercase() }
        persistCards(cards, "card_saved")
    }

    fun delete(card: YNfcCard) {
        val cards = mutableState.value.cards.filterNot {
            it.uid.equals(card.uid, true)
        }
        persistCards(cards, "card_deleted")
    }

    private fun persistCards(cards: List<YNfcCard>, successToken: String) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true)
            runCatching {
                withContext(Dispatchers.IO) { repository.saveCards(cards) }
            }.onSuccess {
                mutableState.value = mutableState.value.copy(
                    busy = false, cards = cards, statusToken = successToken,
                )
            }.onFailure {
                mutableState.value = mutableState.value.copy(
                    busy = false, statusToken = "operation_failed",
                )
            }
        }
    }
    fun apply(card: YNfcCard) = operation("applying") { repository.apply(card) }
    fun stop() = operation("stopping") { repository.stop() }
    fun collectDiagnostics() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { repository.diagnostics() }.getOrElse { it.stackTraceToString() } }
            mutableState.value = mutableState.value.copy(diagnostics = text)
        }
    }
    fun exportDiagnostics() {
        val text = mutableState.value.diagnostics
        if (text.isBlank()) return
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { repository.export(text) }
            mutableState.value = mutableState.value.copy(exportUri = uri)
        }
    }
    private fun operation(started: String, block: suspend () -> Pair<YNfcRuntimeSnapshot, String>) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, statusToken = started)
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { result ->
                    mutableState.value = mutableState.value.copy(busy = false, runtime = result.first, statusToken = result.second)
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(busy = false, statusToken = "operation_failed")
                }
        }
    }
    private suspend fun refresh() {
        runCatching {
            withContext(Dispatchers.IO) {
                val root = repository.rootStatus()
                val hook = repository.hookStatus()
                val runtime = if (root == CapabilityStatus.Available) {
                    repository.runtime()
                } else {
                    mutableState.value.runtime
                }
                Triple(root, hook, runtime)
            }
        }.onSuccess { snapshot ->
            if (!mutableState.value.busy) {
                mutableState.value = mutableState.value.copy(
                    rootStatus = snapshot.first,
                    hookStatus = snapshot.second,
                    runtime = snapshot.third,
                )
            }
        }
    }
    class Factory(private val environment: YNfcEnvironment) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = YNfcViewModel(environment) as T
    }
}
