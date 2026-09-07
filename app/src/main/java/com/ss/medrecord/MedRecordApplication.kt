package com.ss.medrecord

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.ss.medrecord.core.notification.NotificationChannels
import com.ss.medrecord.data.notification.PushTokenRegistrar
import com.ss.medrecord.domain.reminder.ReminderScheduler
import com.ss.medrecord.domain.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * WorkManager is initialised on demand (its default initialiser is removed in
 * the manifest) so that Hilt can inject dependencies into the sync worker.
 */
@HiltAndroidApp
class MedRecordApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var notificationChannels: NotificationChannels

    @Inject
    lateinit var pushTokenRegistrar: PushTokenRegistrar

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Channels first: everything that posts assumes they exist, and
        // creating one that is already there is a no-op that preserves whatever
        // the user has since changed about it.
        notificationChannels.ensureCreated()
        syncManager.initialize()
        // Registers the twice-daily sweep that repairs a broken alarm chain.
        // It does not rebuild anything here - the sweep and the sign-in path
        // both do that, and doing it on every cold start would scan four tables
        // before the first frame.
        reminderScheduler.initialize()
        pushTokenRegistrar.initialize()

        // The third trigger from spec 6.2. Foreground is the moment the user is
        // most likely to care that their data is current, and it catches the
        // case where the device was offline for the whole periodic window.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    syncManager.syncNow()
                }
            },
        )
    }
}
