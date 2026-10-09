package com.yagay.ysuite.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class YSuiteViewModel<State : Any, Effect : Any>(
    initialState: State,
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val effectsChannel = Channel<Effect>(capacity = Channel.BUFFERED)
    val effects: Flow<Effect> = effectsChannel.receiveAsFlow()

    protected fun updateState(transform: (State) -> State) {
        mutableState.update(transform)
    }

    protected fun emitEffect(effect: Effect) {
        viewModelScope.launch {
            effectsChannel.send(effect)
        }
    }
}
