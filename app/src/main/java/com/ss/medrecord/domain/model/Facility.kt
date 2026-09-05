package com.ss.medrecord.domain.model

import com.ss.medrecord.core.common.toInitials

/**
 * A clinic, hospital or diagnostic centre the account has visited
 * (spec section 4.3).
 *
 * Facilities are scoped to the account rather than to a patient: a family uses
 * the same clinic, and duplicating it per patient would make "visit history at
 * this facility" (spec 5.7) impossible to answer.
 *
 * Coordinates are nullable and stay unset until Phase 8, where saving a
 * facility from the map populates them.
 */
data class Facility(
    val facilityId: String,
    val userId: String,
    val name: String,
    val type: FacilityType,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val phone: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    val hasLocation: Boolean get() = latitude != null && longitude != null

    val initials: String get() = name.toInitials()
}

enum class FacilityType(val label: String) {
    CLINIC("Clinic"),
    HOSPITAL("Hospital"),
    DIAGNOSTIC_CENTER("Diagnostic Center"),
}
