package com.ss.medrecord.data.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.data.local.dao.ConsentDao
import com.ss.medrecord.data.local.dao.UserDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.UserRemoteDataSource
import com.ss.medrecord.domain.model.ConsentRecord
import com.ss.medrecord.domain.model.ConsentType
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.repository.ConsentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsentRepositoryImpl @Inject constructor(
    private val consentDao: ConsentDao,
    private val userDao: UserDao,
    private val userRemote: UserRemoteDataSource,
    private val dispatchers: DispatcherProvider,
) : ConsentRepository {

    override fun observeConsentHistory(userId: String): Flow<List<ConsentRecord>> =
        consentDao.observeConsents(userId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun acceptCurrentConsent(userId: String, version: Int): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val acceptedAt = System.currentTimeMillis()
                // One row per consent type, matching spec section 4.8 - a single
                // combined row could not express partial withdrawal later.
                val records = ConsentType.entries.map { type ->
                    ConsentRecord(
                        consentId = UUID.randomUUID().toString(),
                        userId = userId,
                        consentType = type,
                        version = version,
                        acceptedAt = acceptedAt,
                    )
                }

                consentDao.insertAll(records.map { it.toEntity(syncStatus = SyncStatus.PENDING) })
                userDao.markConsentAccepted(
                    userId = userId,
                    version = version,
                    acceptedAt = acceptedAt,
                )

                // Acceptance is already durable locally; a failed push is left
                // PENDING for the sync worker rather than shown as an error,
                // since the user did give consent and must not be re-prompted.
                runCatching {
                    userRemote.insertConsents(records)
                    userDao.getUser(userId)?.toDomain()?.let { userRemote.upsertUser(it) }
                }
                Unit
            }
        }

    override suspend fun latestAcceptedVersion(userId: String): Int? =
        withContext(dispatchers.io) { consentDao.getLatestAcceptedVersion(userId) }
}
