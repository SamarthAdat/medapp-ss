package com.ss.medrecord.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
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
}
