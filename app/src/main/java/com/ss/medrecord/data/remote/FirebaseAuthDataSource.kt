package com.ss.medrecord.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.ss.medrecord.core.common.AppError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over [FirebaseAuth]. Its job is to turn callback-and-exception
 * SDK calls into suspend functions and to map Firebase exception types onto
 * [AppError], so nothing above this layer imports a Firebase class.
 */
@Singleton
class FirebaseAuthDataSource @Inject constructor(
    private val auth: FirebaseAuth,
) {

    val currentUserId: String? get() = auth.currentUser?.uid

    val currentUser: FirebaseUser? get() = auth.currentUser

    /**
     * Emits the signed-in uid, or null when signed out. Backed by Firebase's own
     * auth state listener so a token revoked on another device propagates here.
     */
    fun observeAuthState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): String {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        return requireNotNull(result.user).uid
    }

    suspend fun signUp(name: String, email: String, password: String): String {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = requireNotNull(result.user)
        // Keeps the Firebase profile consistent with our Firestore user doc.
        user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
        return user.uid
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    companion object {
        /** Maps a Firebase Auth throwable onto the app-wide error vocabulary. */
        fun mapAuthError(throwable: Throwable): AppError = when (throwable) {
            is CancellationException -> throw throwable
            is FirebaseAuthWeakPasswordException ->
                AppError.Auth(AppError.AuthReason.WEAK_PASSWORD, throwable)

            is FirebaseAuthUserCollisionException ->
                AppError.Auth(AppError.AuthReason.EMAIL_ALREADY_IN_USE, throwable)

            is FirebaseAuthInvalidUserException ->
                AppError.Auth(AppError.AuthReason.USER_NOT_FOUND, throwable)

            is FirebaseAuthInvalidCredentialsException ->
                AppError.Auth(AppError.AuthReason.INVALID_CREDENTIALS, throwable)

            is IOException -> AppError.Network(throwable)

            else -> when {
                // Firebase reports throttling only through the message text.
                throwable.message?.contains("blocked all requests", ignoreCase = true) == true ->
                    AppError.Auth(AppError.AuthReason.TOO_MANY_REQUESTS, throwable)

                else -> AppError.Unknown(throwable)
            }
        }
    }
}
