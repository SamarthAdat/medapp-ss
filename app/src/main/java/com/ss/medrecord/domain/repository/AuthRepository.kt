package com.ss.medrecord.domain.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.domain.model.AppUser
import kotlinx.coroutines.flow.Flow

/**
 * Identity and account-holder profile. The implementation lives in the data
 * layer; ViewModels depend only on this interface, which keeps Firebase out of
 * the modules under test.
 */
interface AuthRepository {

    /** Emits the signed-in uid, or null when signed out. */
    val authState: Flow<String?>

    val currentUserId: String?

    /** The locally cached account holder. Reads from Room, so it works offline. */
    fun observeCurrentUser(): Flow<AppUser?>

    /** As [observeCurrentUser] but for an already-known uid. */
    fun observeUser(userId: String): Flow<AppUser?>

    suspend fun signIn(email: String, password: String): DataResult<String>

    suspend fun signUp(name: String, email: String, password: String): DataResult<String>

    suspend fun sendPasswordReset(email: String): DataResult<Unit>

    /** Signs out and clears local records so the next account starts clean. */
    suspend fun signOut(): DataResult<Unit>

    /** Pulls the Firestore user doc into Room. No-op when offline. */
    suspend fun refreshUser(userId: String): DataResult<AppUser?>

    /**
     * Guarantees a local row exists for [userId], creating one from the cached
     * Firebase credential if Room has none. Signing in on a second device while
     * offline would otherwise leave the account with no local profile, and the
     * consent ledger has a foreign key onto it.
     */
    suspend fun ensureLocalUser(userId: String): DataResult<AppUser>
}
