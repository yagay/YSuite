package com.yagay.ysuite.platform

interface AppLogger {
    fun debug(tag: String, message: String)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}

interface RootGateway {
    suspend fun isAvailable(): Boolean
}

interface HookGateway {
    suspend fun isAvailable(): Boolean
    suspend fun reload(): Boolean
}
