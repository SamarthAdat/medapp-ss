package com.ss.medrecord.di

import android.content.Context
import androidx.work.WorkManager
import com.ss.medrecord.data.audit.AuditLoggerImpl
import com.ss.medrecord.data.sync.AuditSyncer
import com.ss.medrecord.data.sync.ConsentSyncer
import com.ss.medrecord.data.sync.FacilitySyncer
import com.ss.medrecord.data.sync.PatientSyncer
import com.ss.medrecord.data.sync.ReportSyncer
import com.ss.medrecord.data.sync.UserSyncer
import com.ss.medrecord.data.sync.VisitSyncer
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.sync.EntitySyncer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Syncers are contributed into a set rather than listed in the worker, so
 * adding visits, reports or medicines in a later phase is a one-line binding
 * and the worker never changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindingsModule {

    @Binds
    @IntoSet
    abstract fun bindUserSyncer(impl: UserSyncer): EntitySyncer

    @Binds
    @IntoSet
    abstract fun bindConsentSyncer(impl: ConsentSyncer): EntitySyncer

    @Binds
    @IntoSet
    abstract fun bindPatientSyncer(impl: PatientSyncer): EntitySyncer

    @Binds
    @IntoSet
    abstract fun bindFacilitySyncer(impl: FacilitySyncer): EntitySyncer

    @Binds
    @IntoSet
    abstract fun bindVisitSyncer(impl: VisitSyncer): EntitySyncer

    @Binds
    @IntoSet
    abstract fun bindReportSyncer(impl: ReportSyncer): EntitySyncer

    @Binds
    @IntoSet
    abstract fun bindAuditSyncer(impl: AuditSyncer): EntitySyncer

    @Binds
    @Singleton
    abstract fun bindAuditLogger(impl: AuditLoggerImpl): AuditLogger
}

@Module
@InstallIn(SingletonComponent::class)
object SyncProvidersModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
