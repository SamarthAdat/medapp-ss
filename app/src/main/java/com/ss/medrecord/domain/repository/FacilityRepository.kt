package com.ss.medrecord.domain.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.domain.model.Facility
import kotlinx.coroutines.flow.Flow

interface FacilityRepository {

    fun observeFacilities(userId: String): Flow<List<Facility>>

    fun observeFacility(facilityId: String): Flow<Facility?>

    suspend fun getFacility(facilityId: String): Facility?

    suspend fun createFacility(facility: Facility): DataResult<String>

    suspend fun updateFacility(facility: Facility): DataResult<Unit>

    suspend fun deleteFacility(facilityId: String): DataResult<Unit>

    /**
     * Returns the existing facility with this name, or creates one. Lets the
     * add-visit form accept a typed clinic name without quietly accumulating a
     * duplicate row every time the same place is entered.
     */
    suspend fun findOrCreateByName(
        userId: String,
        name: String,
        type: com.ss.medrecord.domain.model.FacilityType,
    ): DataResult<Facility>

    suspend fun refreshFacilities(userId: String): DataResult<Unit>
}
