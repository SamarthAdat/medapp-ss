package com.ss.medrecord.di

import com.ss.medrecord.core.common.DefaultDispatcherProvider
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.core.connectivity.ConnectivityObserver
import com.ss.medrecord.core.connectivity.ConnectivityObserverImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Bindings for interface -> implementation pairs that live for the app session. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoreBindingsModule {

    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(
        impl: ConnectivityObserverImpl,
    ): ConnectivityObserver
}

/** Constructed objects that cannot be provided by constructor injection alone. */
@Module
@InstallIn(SingletonComponent::class)
object CoreProvidersModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    /**
     * SupervisorJob so one failing long-lived collector cannot cancel the rest
     * of the app-scoped work alongside it.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatchers: DispatcherProvider): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)
}
