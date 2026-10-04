package com.yagay.ysuite.common

data class YSuiteError(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val cause: Throwable? = null,
)

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>

    data class Failure(
        val error: YSuiteError,
    ) : Outcome<Nothing> {
        constructor(
            message: String,
            cause: Throwable? = null,
            code: String = "unknown",
            retryable: Boolean = false,
        ) : this(
            YSuiteError(
                code = code,
                message = message,
                retryable = retryable,
                cause = cause,
            ),
        )

        val message: String
            get() = error.message

        val cause: Throwable?
            get() = error.cause
    }
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }

inline fun <T, R> Outcome<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (YSuiteError) -> R,
): R =
    when (this) {
        is Outcome.Success -> onSuccess(value)
        is Outcome.Failure -> onFailure(error)
    }
