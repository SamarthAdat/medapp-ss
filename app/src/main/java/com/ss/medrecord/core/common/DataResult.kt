package com.ss.medrecord.core.common

/**
 * Outcome of an operation that can fail. Named [DataResult] rather than `Result`
 * to avoid colliding with `kotlin.Result`, which has different semantics.
 */
sealed interface DataResult<out T> {
    data class Success<out T>(val data: T) : DataResult<T>
    data class Error(val error: AppError) : DataResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    companion object {
        /** Runs [block], mapping any thrown exception through [transform]. */
        inline fun <T> catching(
            transform: (Throwable) -> AppError = { AppError.Unknown(it) },
            block: () -> T,
        ): DataResult<T> = try {
            Success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // never swallow cancellation
        } catch (e: Throwable) {
            Error(transform(e))
        }
    }
}

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data))
    is DataResult.Error -> this
}

inline fun <T> DataResult<T>.onSuccess(action: (T) -> Unit): DataResult<T> = apply {
    if (this is DataResult.Success) action(data)
}

inline fun <T> DataResult<T>.onError(action: (AppError) -> Unit): DataResult<T> = apply {
    if (this is DataResult.Error) action(error)
}

fun <T> DataResult<T>.getOrNull(): T? = (this as? DataResult.Success)?.data
