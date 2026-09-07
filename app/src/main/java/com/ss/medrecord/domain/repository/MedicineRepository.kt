package com.ss.medrecord.domain.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineWithContext
import kotlinx.coroutines.flow.Flow

interface MedicineRepository {

    fun observeMedicines(userId: String): Flow<List<MedicineWithContext>>

    fun observeMedicinesForPatient(patientId: String): Flow<List<Medicine>>

    fun observeMedicinesForVisit(visitId: String): Flow<List<Medicine>>

    fun observeMedicine(medicineId: String): Flow<Medicine?>

    fun observeActiveCountForPatient(patientId: String): Flow<Int>

    fun observeCountForVisit(visitId: String): Flow<Int>

    suspend fun getMedicine(medicineId: String): Medicine?

    suspend fun createMedicine(medicine: Medicine): DataResult<String>

    suspend fun updateMedicine(medicine: Medicine): DataResult<Unit>

    /** The pause/resume toggle, kept separate from a full edit. */
    suspend fun setActive(medicineId: String, isActive: Boolean): DataResult<Unit>

    suspend fun deleteMedicine(medicineId: String): DataResult<Unit>

    suspend fun refreshMedicines(userId: String): DataResult<Unit>
}
