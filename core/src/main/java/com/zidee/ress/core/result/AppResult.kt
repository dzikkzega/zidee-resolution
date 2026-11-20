package com.zidee.ress.core.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * Typed success/failure container used on every public boundary that can fail.
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

val AppResult<*>.isSuccess: Boolean get() = this is AppResult.Success

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

fun AppResult<*>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> =
    when (this) {
        is AppResult.Success -> transform(value)
        is AppResult.Failure -> this
    }

inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (AppError) -> R,
): R =
    when (this) {
        is AppResult.Success -> onSuccess(value)
        is AppResult.Failure -> onFailure(error)
    }

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

/**
 * Runs [block] and wraps its outcome. [CancellationException] is always rethrown so
 * structured concurrency keeps working; every other throwable goes through [mapError].
 */
inline fun <T> appResultOf(
    mapError: (Throwable) -> AppError = { AppError.Unknown(it.message ?: it::class.java.name, it) },
    block: () -> T,
): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        AppResult.Failure(mapError(t))
    }
