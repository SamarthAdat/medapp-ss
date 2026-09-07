package com.ss.medrecord.di

import com.ss.medrecord.data.reminder.ReminderRepositoryImpl
import com.ss.medrecord.data.reminder.WorkReminderScheduler
import com.ss.medrecord.data.repository.AuthRepositoryImpl
import com.ss.medrecord.data.repository.ConsentRepositoryImpl
import com.ss.medrecord.data.repository.FacilityRepositoryImpl
import com.ss.medrecord.data.repository.MedicineRepositoryImpl
import com.ss.medrecord.data.repository.PatientRepositoryImpl
import com.ss.medrecord.data.repository.ReportRepositoryImpl
import com.ss.medrecord.data.repository.VisitRepositoryImpl
import com.ss.medrecord.domain.reminder.ReminderScheduler
import com.ss.medrecord.domain.repository.AuthRepository
import com.ss.medrecord.domain.repository.ConsentRepository
import com.ss.medrecord.domain.repository.FacilityRepository
import com.ss.medrecord.domain.repository.MedicineRepository
import com.ss.medrecord.domain.repository.PatientRepository
import com.ss.medrecord.domain.repository.ReminderRepository
import com.ss.medrecord.domain.repository.ReportRepository
import com.ss.medrecord.domain.repository.VisitRepository
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

    @Binds
    @Singleton
    abstract fun bindPatientRepository(impl: PatientRepositoryImpl): PatientRepository

    @Binds
    @Singleton
    abstract fun bindFacilityRepository(impl: FacilityRepositoryImpl): FacilityRepository

    @Binds
    @Singleton
    abstract fun bindVisitRepository(impl: VisitRepositoryImpl): VisitRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds
    @Singleton
    abstract fun bindMedicineRepository(impl: MedicineRepositoryImpl): MedicineRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: WorkReminderScheduler): ReminderScheduler
}
