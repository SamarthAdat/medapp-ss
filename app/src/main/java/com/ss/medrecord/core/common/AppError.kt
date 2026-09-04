package com.ss.medrecord.core.common

/**
 * Every failure that can surface in the UI. Repositories translate platform
 * exceptions (Firebase, Room, IO) into one of these so ViewModels never have to
 * reason about SDK-specific throwables.
 */
sealed class AppError(
    open val cause: Throwable? = null,
) {
    /** No connectivity, or a request that timed out. Usually retryable. */
    data class Network(override val cause: Throwable? = null) : AppError(cause)

    /** Sign-in / session problems: bad credentials, expired token, no session. */
    data class Auth(val reason: AuthReason, override val cause: Throwable? = null) : AppError(cause)

    /** Local database read/write failure, including decryption failures. */
    data class Database(override val cause: Throwable? = null) : AppError(cause)

    /** Remote file storage upload/download failure. */
    data class Storage(override val cause: Throwable? = null) : AppError(cause)

    /** User input that failed a business rule; [message] is user-presentable. */
    data class Validation(val message: String) : AppError(null)

    /** The record was not found locally or remotely. */
    data object NotFound : AppError(null)

    /** Caller is not allowed to touch this record (security-rule rejection). */
    data object PermissionDenied : AppError(null)

    data class Unknown(override val cause: Throwable? = null) : AppError(cause)

    enum class AuthReason {
        INVALID_CREDENTIALS,
        EMAIL_ALREADY_IN_USE,
        WEAK_PASSWORD,
        USER_NOT_FOUND,
        SESSION_EXPIRED,
        NOT_AUTHENTICATED,
        TOO_MANY_REQUESTS,
    }
}
