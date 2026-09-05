package com.ss.medrecord.domain.model

import com.ss.medrecord.core.common.toInitials
import java.time.LocalDate
import java.time.Period

/**
 * A person whose records are kept under one account (spec section 4.2). Every
 * visit, report, medicine and reminder in later phases is scoped to a
 * [patientId], so this is the pivot the whole app filters on.
 *
 * [dateOfBirth] is an epoch day rather than a timestamp: a birth date has no
 * time or zone, and storing it as millis makes it shift by a day whenever the
 * device crosses a timezone.
 */
data class Patient(
    val patientId: String,
    val userId: String,
    val name: String,
    val relationship: Relationship,
    val dateOfBirthEpochDay: Long? = null,
    val gender: Gender? = null,
    val bloodGroup: BloodGroup? = null,
    val knownAllergies: String? = null,
    val photoUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean = false,
    /**
     * Set when the user deletes the patient. The record stays on disk until the
     * grace period expires (spec section 9.6) so that erasure and audit
     * retention can both be satisfied.
     */
    val deletedAt: Long? = null,
) {
    val dateOfBirth: LocalDate? get() = dateOfBirthEpochDay?.let(LocalDate::ofEpochDay)

    val isDeleted: Boolean get() = deletedAt != null

    /** Whole years, or null when no date of birth was recorded. */
    fun ageYears(today: LocalDate = LocalDate.now()): Int? {
        val dob = dateOfBirth ?: return null
        if (dob.isAfter(today)) return null
        return Period.between(dob, today).years
    }

    val initials: String get() = name.toInitials()
}

/** How the patient relates to the account holder (spec section 4.2). */
enum class Relationship(val label: String) {
    SELF("Self"),
    SPOUSE("Spouse"),
    CHILD("Child"),
    PARENT("Parent"),
    DEPENDENT("Dependent"),
    OTHER("Other"),
}

enum class Gender(val label: String) {
    FEMALE("Female"),
    MALE("Male"),
    OTHER("Other"),

    /**
     * Distinct from leaving the field unset: the person was asked and chose not
     * to answer. Both are valid, and collapsing them would lose that.
     */
    PREFER_NOT_TO_SAY("Prefer not to say"),
}

enum class BloodGroup(val label: String) {
    A_POSITIVE("A+"),
    A_NEGATIVE("A-"),
    B_POSITIVE("B+"),
    B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"),
    AB_NEGATIVE("AB-"),
    O_POSITIVE("O+"),
    O_NEGATIVE("O-"),
}
