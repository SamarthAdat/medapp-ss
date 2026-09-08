package com.ss.medrecord.core.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The optional app lock (spec section 9.2 - access control on the device
 * itself, not on the account).
 *
 * What this is *not*: it is not a second encryption key. The database is
 * already sealed with a hardware-backed Keystore key, and the fingerprint does
 * not unseal it. This is a screen lock - it stops someone who has picked up an
 * unlocked phone from reading a family's medical history, which is the threat
 * a person actually faces day to day.
 *
 * Saying that plainly matters, because a lock that implies more protection
 * than it gives is worse than no lock: it changes what the user is willing to
 * leave the phone lying next to.
 */
@Singleton
class BiometricGate @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Fingerprint, face, iris - *or* the device PIN.
     *
     * The credential fallback is included on purpose. Enrolments get wiped by
     * a factory reset and sensors fail; without a fallback the user's own
     * records would be behind a door that no longer has a key, and the only
     * way back in would be to clear the app's data.
     */
    private val allowedAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** Whether this device can satisfy a lock request right now. */
    fun availability(): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(allowedAuthenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricAvailability.NotEnrolled

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            ->
                BiometricAvailability.NoHardware

            // Security patch out of date, vendor bug, or a status this version
            // of the library does not model. Treated as unavailable rather than
            // as an error: there is nothing the user can do about it here.
            else -> BiometricAvailability.Unavailable
        }

    val isAvailable: Boolean get() = availability() == BiometricAvailability.Available

    /**
     * Shows the system prompt and suspends until it resolves.
     *
     * Takes the activity rather than holding one, because [BiometricPrompt]
     * attaches to the activity's fragment manager - a retained reference here
     * would be a leaked activity for the life of the process.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock MedRecord",
        subtitle: String = "Confirm it is you before your records are shown",
    ): BiometricResult = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(BiometricResult.Succeeded)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!continuation.isActive) return
                    // A cancel is the user declining, not a failure to report.
                    // Distinguishing them is what lets the caller stay silent
                    // on a back press and speak up on a locked-out sensor.
                    val outcome = when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED,
                        -> BiometricResult.Cancelled

                        else -> BiometricResult.Failed(errString.toString())
                    }
                    continuation.resume(outcome)
                }

                // Deliberately not resumed: a rejected finger means try again,
                // and the system prompt is still on screen saying so.
                override fun onAuthenticationFailed() = Unit
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(info)
    }
}

/** Why the device can or cannot lock the app. */
enum class BiometricAvailability {
    Available,
    NotEnrolled,
    NoHardware,
    Unavailable,
    ;

    /**
     * The reason, phrased for someone who is not going to look up what
     * "BIOMETRIC_ERROR_NONE_ENROLLED" means.
     */
    val explanation: String?
        get() = when (this) {
            Available -> null
            NotEnrolled -> "Add a fingerprint or screen lock in your phone's settings first."
            NoHardware -> "This phone has no fingerprint sensor."
            Unavailable -> "Your phone cannot use this right now."
        }
}

sealed interface BiometricResult {
    data object Succeeded : BiometricResult
    /** The user backed out. Nothing to report. */
    data object Cancelled : BiometricResult
    data class Failed(val message: String) : BiometricResult
}
