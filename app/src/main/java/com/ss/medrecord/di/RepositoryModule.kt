package com.ss.medrecord.di

import com.ss.medrecord.data.repository.AuthRepositoryImpl
import com.ss.medrecord.data.repository.ConsentRepositoryImpl
import com.ss.medrecord.domain.repository.AuthRepository
import com.ss.medrecord.domain.repository.ConsentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindConsentRepository(impl: ConsentRepositoryImpl): ConsentRepository
}
