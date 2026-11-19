package com.yagay.YNFC

/**
 * Pure user-visible evidence semantics. A request dispatched by JNI (void)
 * must never become VERIFIED RF evidence just because its PID/epoch is new.
 */
internal object RuntimePresentationPolicy {
    fun currentUidRestore(map: Map<String, String>, currentPid: Int, epoch: Long): Boolean {
        val generation = map["command_generation"]?.toLongOrNull() ?: return false
        return currentPid > 0 && epoch > 0L && generation > 0L &&
            map["simulation_enabled"] == "true" &&
            map["simulation_backend"] == "RF" &&
            map["command_action"] == "APPLY" &&
            map["command_status"] == "UNVERIFIED" &&
            map["command_handled_generation"]?.toLongOrNull() == generation &&
            map["rf_source"] == "own-nci-0x33" &&
            !map["uid"].isNullOrBlank() &&
            map["own_nci_lifecycle_stage"] == "WRITE_REQUESTED_RF_UNVERIFIED" &&
            map["own_nci_lifecycle_generation"]?.toLongOrNull() == generation &&
            map["own_nci_lifecycle_pid"]?.toIntOrNull() == currentPid &&
            map["own_nci_lifecycle_epoch"]?.toLongOrNull() == epoch &&
            (map["own_nci_lifecycle_at"]?.toLongOrNull() ?: 0L) > 0L
    }

    fun hasPollingHook(capabilities: String?): Boolean {
        if (capabilities.isNullOrBlank()) return false
        val fields = capabilities.split(';')
        return fields.any { it.trim() == "compute=true" } ||
            (fields.firstOrNull { it.startsWith("driverHooks=") }
                ?.substringAfter('=')?.toIntOrNull() ?: 0) > 0
    }

    fun pollingStatus(disabled: Boolean, status: String, capabilities: String?): String =
        if (disabled && !capabilities.isNullOrBlank() && !hasPollingHook(capabilities))
            "UNSUPPORTED_NO_DISCOVERY_HOOKS"
        else status
}
