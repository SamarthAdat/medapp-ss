package com.ss.medrecord.data.repository

import com.ss.medrecord.core.common.AppError
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.core.file.CameraCaptureStore
import com.ss.medrecord.core.file.EncryptedFileStore
import com.ss.medrecord.data.local.dao.AuditLogDao
import com.ss.medrecord.data.local.dao.UserDao
import com.ss.medrecord.data.local.datastore.ActivePatientStore
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.FirebaseAuthDataSource
import com.ss.medrecord.data.remote.UserRemoteDataSource
import com.ss.medrecord.domain.model.AppUser
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authDataSource: FirebaseAuthDataSource,
    private val userRemote: UserRemoteDataSource,
    private val userDao: UserDao,
    private val auditLogDao: AuditLogDao,
    private val activePatientStore: ActivePatientStore,
    private val fileStore: EncryptedFileStore,
    private val cameraCaptureStore: CameraCaptureStore,
    private val dispatchers: DispatcherProvider,
) : AuthRepository {

    override val authState: Flow<String?> = authDataSource.observeAuthState()

    override val currentUserId: String? get() = authDataSource.currentUserId

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentUser(): Flow<AppUser?> =
        authState.flatMapLatest { userId ->
            if (userId == null) flowOf(null) else userDao.observeUser(userId).map { it?.toDomain() }
        }

    override fun observeUser(userId: String): Flow<AppUser?> =
        userDao.observeUser(userId).map { it?.toDomain() }

    override suspend fun signIn(email: String, password: String): DataResult<String> =
        withContext(dispatchers.io) {
            DataResult.catching(FirebaseAuthDataSource::mapAuthError) {
                val userId = authDataSource.signIn(email, password)
                // Best effort: a successful sign-in must not fail because the
                // profile pull did not complete. Room already has it, or the
                // session gate will provision it from the cached credential.
                runCatching { pullUserIntoRoom(userId) }
                userId
            }
        }

    override suspend fun signUp(name: String, email: String, password: String): DataResult<String> =
        withContext(dispatchers.io) {
            DataResult.catching(FirebaseAuthDataSource::mapAuthError) {
                val userId = authDataSource.signUp(name, email, password)
                val now = System.currentTimeMillis()
                val user = AppUser(
                    userId = userId,
                    name = name.trim(),
                    email = email.trim(),
                    createdAt = now,
                    updatedAt = now,
                )
                // Local first, so the account exists for the app even if the
                // Firestore write is lost to a dropped connection.
                userDao.upsert(user.toEntity(syncStatus = SyncStatus.PENDING))
                runCatching { userRemote.upsertUser(user) }
                    .onSuccess {
                        userDao.upsert(user.toEntity(syncStatus = SyncStatus.SYNCED))
                    }
                userId
            }
        }

    override suspend fun sendPasswordReset(email: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(FirebaseAuthDataSource::mapAuthError) {
                authDataSource.sendPasswordReset(email)
            }
        }

    override suspend fun signOut(): DataResult<Unit> = withContext(dispatchers.io) {
        DataResult.catching({ AppError.Unknown(it) }) {
            authDataSource.signOut()
            // Local rows are dropped so a different account signing in on this
            // device can never read the previous holder's cached records.
            // Patients cascade from the user row; the remembered selection lives
            // outside the database and has to be cleared explicitly.
            userDao.deleteAll()
            activePatientStore.clear()
            // Report rows cascade away with the user row, but their encrypted
            // files are on the filesystem and would otherwise outlive the
            // account. Every one of them is already in Cloud Storage or was
            // never uploaded from a session that has now ended.
            fileStore.deleteAll()
            cameraCaptureStore.clear()
            // Audit entries are evidence, not cache, so only the ones already
            // safe in Firestore are dropped. Unpushed entries stay - they are
            // encrypted at rest, scoped to their own userId by every query, and
            // discarding them would lose the record of what was done.
            auditLogDao.deleteSynced()
        }
    }

    override suspend fun refreshUser(userId: String): DataResult<AppUser?> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                pullUserIntoRoom(userId)
            }
        }

    override suspend fun ensureLocalUser(userId: String): DataResult<AppUser> =
        withContext(dispatchers.io) {
            DataResult.catching({ AppError.Database(it) }) {
                userDao.getUser(userId)?.toDomain()
                    ?: runCatching { pullUserIntoRoom(userId) }.getOrNull()
                    ?: createLocalUserFromCredential(userId)
            }
        }

    /**
     * Builds a minimal local profile from the Firebase credential, which the
     * SDK keeps cached on device and so is readable offline. Marked PENDING so
     * the sync worker reconciles it against Firestore once there is a network.
     */
    private suspend fun createLocalUserFromCredential(userId: String): AppUser {
        val credential = authDataSource.currentUser
        val now = System.currentTimeMillis()
        val user = AppUser(
            userId = userId,
            name = credential?.displayName.orEmpty(),
            email = credential?.email.orEmpty(),
            createdAt = now,
            updatedAt = now,
        )
        userDao.upsert(user.toEntity(syncStatus = SyncStatus.PENDING))
        return user
    }

    /**
     * Merges the remote profile into Room. The remote copy wins only when it is
     * at least as recent as the local one, so an unsynced local edit made while
     * offline is not silently overwritten by a stale server document.
     */
    private suspend fun pullUserIntoRoom(userId: String): AppUser? {
        val remote = userRemote.getUser(userId) ?: return userDao.getUser(userId)?.toDomain()
        val local = userDao.getUser(userId)
        if (local == null || remote.updatedAt >= local.updatedAt) {
            userDao.upsert(remote.toEntity(syncStatus = SyncStatus.SYNCED))
            return remote
        }
        return local.toDomain()
    }
}
