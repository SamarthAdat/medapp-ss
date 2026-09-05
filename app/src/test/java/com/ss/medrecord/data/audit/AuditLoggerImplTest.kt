package com.ss.medrecord.data.audit

import com.ss.medrecord.core.device.DeviceIdProvider
import com.ss.medrecord.data.local.dao.AuditLogDao
import com.ss.medrecord.data.local.entity.AuditLogEntity
import com.ss.medrecord.data.remote.FirebaseAuthDataSource
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.SyncStatus
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditLoggerImplTest {

    private val dao = mockk<AuditLogDao>(relaxed = true)
    private val auth = mockk<FirebaseAuthDataSource>()
    private val deviceId = mockk<DeviceIdProvider>()

    private val logger = AuditLoggerImpl(dao, auth, deviceId)

    @Test
    fun `an entry captures who did what to which record`() = runTest {
        every { auth.currentUserId } returns USER_ID
        every { deviceId.deviceIdHash } returns DEVICE_HASH
        val captured = slot<AuditLogEntity>()
        coEvery { dao.insert(capture(captured)) } just Runs

        logger.log(
            action = AuditAction.DELETE,
            entityType = AuditEntityType.PATIENT,
            entityId = "p1",
            patientId = "p1",
        )

        with(captured.captured) {
            assertEquals(USER_ID, userId)
            assertEquals(AuditAction.DELETE, action)
            assertEquals(AuditEntityType.PATIENT, entityType)
            assertEquals("p1", entityId)
            assertEquals("p1", patientId)
            assertEquals(DEVICE_HASH, deviceIdHash)
            // PENDING so the sync worker ships it to where it becomes
            // tamper-resistant.
            assertEquals(SyncStatus.PENDING, syncStatus)
            assertTrue(timestamp > 0)
        }
    }

    @Test
    fun `entries get distinct ids`() = runTest {
        every { auth.currentUserId } returns USER_ID
        every { deviceId.deviceIdHash } returns DEVICE_HASH
        val captured = mutableListOf<AuditLogEntity>()
        coEvery { dao.insert(capture(captured)) } just Runs

        repeat(2) {
            logger.log(AuditAction.VIEW, AuditEntityType.PATIENT, "p1")
        }

        assertNotEquals(captured[0].logId, captured[1].logId)
    }

    @Test
    fun `a database failure never propagates to the caller`() = runTest {
        // Refusing to save a medical record because an audit row could not be
        // written would be worse for the patient than a gap in the trail.
        every { auth.currentUserId } returns USER_ID
        every { deviceId.deviceIdHash } returns DEVICE_HASH
        coEvery { dao.insert(any()) } throws IllegalStateException("disk full")

        logger.log(AuditAction.CREATE, AuditEntityType.PATIENT, "p1")
    }

    @Test
    fun `nothing is written without a session`() = runTest {
        every { auth.currentUserId } returns null

        logger.log(AuditAction.CREATE, AuditEntityType.PATIENT, "p1")

        coVerify(exactly = 0) { dao.insert(any()) }
    }

    private companion object {
        const val USER_ID = "user-1"
        const val DEVICE_HASH = "abc123"
    }
}
