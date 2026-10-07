package com.yagay.ysuite.feature.ynfc
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.YNFC.CardModel
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
    val cards: List<CardModel> = emptyList(),
    val runtime: YNfcRuntimeSnapshot = YNfcRuntimeSnapshot(),
    val rootStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val hookStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val busy: Boolean = false,
    val message: String? = null,
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
    fun saveCard(card: CardModel) {
        val cards = (mutableState.value.cards.filterNot { it.uid.equals(card.uid, true) } + card).sortedBy { it.name.lowercase() }
        repository.saveCards(cards)
        mutableState.value = mutableState.value.copy(cards = cards, message = "card_saved")
    }
    fun delete(card: CardModel) {
        val cards = mutableState.value.cards.filterNot { it.uid.equals(card.uid, true) }
        repository.saveCards(cards)
        mutableState.value = mutableState.value.copy(cards = cards)
    }
    fun apply(card: CardModel) = operation("applying") { repository.apply(card) }
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
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, message = started)
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { result ->
                    mutableState.value = mutableState.value.copy(busy = false, runtime = result.first, message = result.second)
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(busy = false, message = it.message ?: "operation_failed")
                }
        }
    }
    private suspend fun refresh() {
        runCatching {
            val root = repository.rootStatus()
            val hook = repository.hookStatus()
            val runtime = if (root == CapabilityStatus.Available) repository.runtime() else mutableState.value.runtime
            Triple(root, hook, runtime)
        }.onSuccess {
            mutableState.value = mutableState.value.copy(rootStatus = it.first, hookStatus = it.second, runtime = it.third)
        }
    }
    class Factory(private val environment: YNfcEnvironment) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = YNfcViewModel(environment) as T
    }
}
