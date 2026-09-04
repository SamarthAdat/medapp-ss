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
}
