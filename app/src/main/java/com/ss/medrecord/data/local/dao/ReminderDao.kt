package com.ss.medrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ss.medrecord.data.local.entity.ReminderEntity
import com.ss.medrecord.domain.model.ReminderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    /**
     * IGNORE, not REPLACE or upsert, and it is the whole idempotency story.
     *
     * Regeneration re-derives the same (source_id, trigger_at_millis) pairs
     * every sweep. Under the unique index on that pair, IGNORE makes a repeated
     * insert a no-op, so a reminder that has already fired or been dismissed
     * keeps its status instead of being resurrected and posted a second time.
     * REPLACE would delete and re-insert, resetting it to SCHEDULED - the exact
     * bug where a user dismisses a dose and is nagged about it again an hour
     * later.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Query(
        """
        SELECT * FROM reminders
        WHERE status = 'SCHEDULED' AND trigger_at_millis > :afterMillis
        ORDER BY trigger_at_millis ASC
        LIMIT 1
        """,
    )
    suspend fun nextScheduled(afterMillis: Long): ReminderEntity?

    /**
     * Everything that has come due but not yet been posted.
     *
     * [notBeforeMillis] discards reminders that went stale while the device was
     * off. Waking someone at 07:00 to tell them about a dose due at 22:00 two
     * days ago is noise, and noise is what gets a medication reminder muted.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE status = 'SCHEDULED'
          AND trigger_at_millis <= :untilMillis
          AND trigger_at_millis >= :notBeforeMillis
        ORDER BY trigger_at_millis ASC
        """,
    )
    suspend fun getDue(untilMillis: Long, notBeforeMillis: Long): List<ReminderEntity>

    /** Today's schedule for the dashboard, fired rows included. */
    @Query(
        """
        SELECT * FROM reminders
        WHERE user_id = :userId
          AND trigger_at_millis BETWEEN :fromMillis AND :toMillis
        ORDER BY trigger_at_millis ASC
        """,
    )
    fun observeBetween(userId: String, fromMillis: Long, toMillis: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE reminder_id = :reminderId")
    suspend fun getReminder(reminderId: Long): ReminderEntity?

    @Query("UPDATE reminders SET status = :status WHERE reminder_id IN (:reminderIds)")
    suspend fun setStatus(reminderIds: List<Long>, status: ReminderStatus)

    /**
     * Drops the future schedule derived from one source, so an edited medicine
     * does not keep reminding on its old times. Only SCHEDULED rows and only
     * future ones: the past is the record of what this device actually posted,
     * and rewriting it would let a fired reminder fire again.
     */
    @Query(
        """
        DELETE FROM reminders
        WHERE source_id = :sourceId
          AND status = 'SCHEDULED'
          AND trigger_at_millis > :afterMillis
        """,
    )
    suspend fun deleteFutureForSource(sourceId: String, afterMillis: Long)

    /** Same, for every source at once - used before a full regeneration. */
    @Query(
        """
        DELETE FROM reminders
        WHERE user_id = :userId
          AND status = 'SCHEDULED'
          AND trigger_at_millis > :afterMillis
        """,
    )
    suspend fun deleteFutureForUser(userId: String, afterMillis: Long)

    /**
     * Housekeeping. Fired and dismissed rows are only kept long enough to stop
     * a sweep re-posting them; past that they are dead weight on a table that
     * grows with every dose of every medicine.
     */
    @Query("DELETE FROM reminders WHERE trigger_at_millis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)

    /** Sign-out: this device's schedule belongs to the session that made it. */
    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
