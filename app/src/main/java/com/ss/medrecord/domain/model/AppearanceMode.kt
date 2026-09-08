package com.ss.medrecord.domain.model

/**
 * Which colour scheme the app draws in.
 *
 * [FOLLOW_SYSTEM] is the default and the honest one: a phone that switches to
 * dark at sunset should take this app with it. The two explicit choices exist
 * because a medical record is often read in conditions the system setting is
 * wrong for - a bright ward at night, a dim room in the afternoon - and being
 * unable to override it is the sort of small friction that makes an app feel
 * like it is arguing with you.
 */
enum class AppearanceMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        /** Unknown stored values fall back rather than throwing. */
        fun fromStorage(value: String?): AppearanceMode =
            entries.firstOrNull { it.name == value } ?: FOLLOW_SYSTEM
    }
}
