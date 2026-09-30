package com.yagay.ydiag

data class ModuleState(
    val connected: Boolean = false,
    val apiVersion: Int? = null,
    val scope: Set<String> = emptySet(),
    val runningTargets: List<String> = emptyList(),
    val loadedPackages: Set<String> = emptySet(),
    val pendingScope: Set<String> = emptySet(),
    val activationPending: Set<String> = emptySet(),
    val restartRequired: Set<String> = emptySet(),
    val systemScoped: Boolean = false,
    val systemLoaded: Boolean = false,
    val message: String = "LSPosed 未连接",
)
