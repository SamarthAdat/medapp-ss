package com.ss.medrecord.core.common

private val WHITESPACE = Regex("""\s+""")

/**
 * Up to two uppercase letters for an avatar placeholder: "Asha Rao" gives "AR",
 * "City Care Clinic" gives "CC".
 *
 * Falls back to "?" rather than an empty string, so a blank or whitespace-only
 * name still renders a legible circle instead of a void.
 */
fun String.toInitials(): String = trim()
    .split(WHITESPACE)
    .filter { it.isNotEmpty() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString(separator = "")
    .ifEmpty { "?" }
