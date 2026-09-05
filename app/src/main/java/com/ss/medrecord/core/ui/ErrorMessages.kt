package com.ss.medrecord.core.ui

import com.ss.medrecord.core.common.AppError

/**
 * Turns an [AppError] into something worth showing a person.
 *
 * Messages deliberately avoid echoing back whether an email address exists on
 * the service: "no account found" and "wrong password" both resolve to the same
 * text, so the sign-in form cannot be used to enumerate registered users.
 */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Network -> "You appear to be offline. Your changes are saved on this device and will sync automatically."
    is AppError.Auth -> when (reason) {
        AppError.AuthReason.INVALID_CREDENTIALS,
        AppError.AuthReason.USER_NOT_FOUND,
        -> "Email or password is incorrect."

        AppError.AuthReason.EMAIL_ALREADY_IN_USE -> "An account already exists for this email."
        AppError.AuthReason.WEAK_PASSWORD -> "That password is too weak. Choose a longer one."
        AppError.AuthReason.SESSION_EXPIRED -> "Your session expired. Please sign in again."
        AppError.AuthReason.NOT_AUTHENTICATED -> "Please sign in to continue."
        AppError.AuthReason.TOO_MANY_REQUESTS -> "Too many attempts. Try again in a few minutes."
    }

    is AppError.Database -> "Could not read local records on this device."
    is AppError.Storage -> "File transfer failed. It will be retried automatically."
    is AppError.Validation -> message
    AppError.NotFound -> "That record no longer exists."
    AppError.PermissionDenied -> "You do not have access to that record."
    is AppError.Unknown -> "Something went wrong. Please try again."
}
