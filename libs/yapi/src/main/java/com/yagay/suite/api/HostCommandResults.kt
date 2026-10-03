package com.yagay.suite.api

/** Normalize host Root failures once so every Feature reports timeout/error/stderr/exit consistently. */
fun HostCommandResult.toTextResult(operation: String): Result<String> =
    if (success) Result.success(stdout)
    else Result.failure(IllegalStateException(rootFailureMessage(operation)))

fun HostCommandResult.rootFailureMessage(operation: String): String = when {
    timedOut -> "Root operation timed out: $operation"
    !errorMessage.isNullOrBlank() -> errorMessage
    stderr.isNotBlank() -> stderr
    code != 0 -> "Root operation failed: $operation (exit=$code)"
    else -> "Root operation failed: $operation"
}
