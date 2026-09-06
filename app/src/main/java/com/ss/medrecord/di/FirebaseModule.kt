package com.ss.medrecord.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Firestore is configured with a memory-only cache.
     *
     * Spec section 6.4 suggests leaving Firestore disk persistence on as a
     * safety net, but that cache is a plain unencrypted SQLite file in app
     * storage, which would put patient data on disk in the clear and contradict
     * section 9.2. The encrypted Room database already provides the offline
     * guarantee, so it stays the only durable local store and Firestore keeps
     * nothing across process death.
     */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
    }

    /**
     * Cloud Storage for report files (spec section 6.2).
     *
     * The SDK's own retry windows are shortened from the ten-minute default:
     * uploads run inside a WorkManager job that already retries with backoff and
     * network constraints, so an attempt that is not going to succeed should
     * fail fast and hand the decision back to the worker rather than holding a
     * job open for ten minutes on a dead connection.
     */
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance().apply {
        maxUploadRetryTimeMillis = TRANSFER_RETRY_MILLIS
        maxDownloadRetryTimeMillis = TRANSFER_RETRY_MILLIS
        maxOperationRetryTimeMillis = TRANSFER_RETRY_MILLIS
    }

    private const val TRANSFER_RETRY_MILLIS = 60_000L
}
