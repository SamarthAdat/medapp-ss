package com.ss.medrecord.domain.model

import java.time.LocalDate

/**
 * One clinic or hospital visit for one patient (spec section 4.4).
 *
 * Dates are epoch days rather than timestamps: a visit happens on a calendar
 * date, and storing millis would shift it whenever the device crosses a
 * timezone.
 *
 * The spec lists prescribedMedicines and attachedReportIds as fields on the
 * visit. They are deliberately not stored here as arrays - medicines and
 * reports each carry their own visitId, so the relationship is queried rather
 * than duplicated. Two copies of the same link is one more thing to keep in
 * step during sync, and the array copy would be the one that goes stale.
 */
data class Visit(
    val visitId: String,
    val userId: String,
    val patientId: String,
    val facilityId: String,
    val doctorName: String? = null,
    val visitDateEpochDay: Long,
    val notes: String? = null,
    val nextVisitDateEpochDay: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val visitDate: LocalDate get() = LocalDate.ofEpochDay(visitDateEpochDay)

    val nextVisitDate: LocalDate? get() = nextVisitDateEpochDay?.let(LocalDate::ofEpochDay)

    val isDeleted: Boolean get() = deletedAt != null

    fun hasUpcomingVisit(today: LocalDate = LocalDate.now()): Boolean =
        nextVisitDate?.isBefore(today) == false

    /** Days until the next visit; negative once it has passed. */
    fun daysUntilNextVisit(today: LocalDate = LocalDate.now()): Long? =
        nextVisitDate?.toEpochDay()?.minus(today.toEpochDay())
}

/** A visit joined to the facility it happened at, for list and detail screens. */
data class VisitWithFacility(
    val visit: Visit,
    val facility: Facility?,
) {
    /** Facilities are soft-deleted, so a missing one is a data gap, not normal. */
    val facilityName: String get() = facility?.name ?: "Unknown facility"
}

/**
 * A visit with only the names a cross-patient view needs.
 *
 * Deliberately not [VisitWithFacility]: the dashboard and the timeline list
 * visits across every patient, where "whose visit was this" matters and the
 * clinic's address and phone number are dead weight on every row.
 */
data class VisitWithContext(
    val visit: Visit,
    val facilityName: String?,
    val patientName: String?,
)
