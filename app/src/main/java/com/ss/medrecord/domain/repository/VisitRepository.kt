package com.ss.medrecord.domain.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.domain.model.Visit
import com.ss.medrecord.domain.model.VisitWithFacility
import kotlinx.coroutines.flow.Flow

interface VisitRepository {

    fun observeVisitsForPatient(patientId: String): Flow<List<VisitWithFacility>>

    fun observeVisit(visitId: String): Flow<VisitWithFacility?>

    fun observeVisitsAtFacility(facilityId: String): Flow<List<Visit>>

    fun observeVisitCount(patientId: String): Flow<Int>

    suspend fun getVisit(visitId: String): Visit?

    suspend fun createVisit(visit: Visit): DataResult<String>

    suspend fun updateVisit(visit: Visit): DataResult<Unit>

    suspend fun deleteVisit(visitId: String): DataResult<Unit>

    suspend fun refreshVisits(userId: String): DataResult<Unit>
}
