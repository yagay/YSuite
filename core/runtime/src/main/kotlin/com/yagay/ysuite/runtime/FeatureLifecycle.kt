package com.yagay.ysuite.runtime

enum class FeatureLifecycleEvent {
    Activated,
    Deactivated,
}

fun interface FeatureLifecycleObserver {
    fun onEvent(event: FeatureLifecycleEvent)

    companion object {
        val None = FeatureLifecycleObserver { }
    }
}
