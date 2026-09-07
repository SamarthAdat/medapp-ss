package com.ss.medrecord.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.ss.medrecord.core.location.Coordinates

/**
 * Handing a facility off to another app.
 *
 * Both of these are deliberately implicit intents rather than in-app features.
 * Turn-by-turn navigation and telephony are things the device already does
 * better than this app could, and routing them out means no extra permission
 * and no location leaving the app under its own name.
 *
 * Every launch is guarded. A device can ship without a dialler (a tablet) or
 * without any maps app, and an unhandled implicit intent throws
 * ActivityNotFoundException straight through to a crash - which is exactly how
 * the Phase 5 picker bug took the process down.
 */

/** Opens directions to a place. Returns false when nothing can handle it. */
fun Context.openDirections(coordinates: Coordinates, label: String?): Boolean {
    // The geo: scheme with a q= label shows the place by name rather than as a
    // bare pin, and is understood by every maps app, not only Google's.
    val encodedLabel = Uri.encode(label.orEmpty())
    val uri = if (label.isNullOrBlank()) {
        "geo:${coordinates.latitude},${coordinates.longitude}".toUri()
    } else {
        ("geo:${coordinates.latitude},${coordinates.longitude}" +
            "?q=${coordinates.latitude},${coordinates.longitude}($encodedLabel)").toUri()
    }

    return runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

/**
 * Opens the dialler with the number filled in.
 *
 * ACTION_DIAL, not ACTION_CALL: dialling needs no permission and leaves the
 * user one deliberate tap away from actually placing the call. An app holding
 * medical records should not be able to silently ring anyone.
 */
fun Context.openDialer(phone: String): Boolean = runCatching {
    startActivity(
        Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(phone)}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}.isSuccess
