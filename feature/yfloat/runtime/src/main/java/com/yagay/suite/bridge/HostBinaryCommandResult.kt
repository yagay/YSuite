package com.yagay.suite.api

data class HostBinaryCommandResult(
    val code: Int,
    val stdout: ByteArray,
    val stderr: String,
    val timedOut: Boolean = false,
    val errorMessage: String? = null,
)
