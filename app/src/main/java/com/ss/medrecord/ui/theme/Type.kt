package com.ss.medrecord.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.ss.medrecord.R

/**
 * Three families, each with a job:
 *
 *  - [Sora] for headings and buttons. Geometric, slightly condensed, negative
 *    tracking. It is what makes a screen recognisable at a glance.
 *  - [PlexSans] for body copy. Chosen over the platform default because it has
 *    a taller x-height, which matters when the copy is clinical and dense.
 *  - [PlexMono] for labels, counts, times and file sizes. Anything the user
 *    might compare down a column is monospaced so the digits line up.
 *
 * The font files are bundled in res/font rather than fetched with downloadable
 * fonts. This app is offline-first; a typeface that only arrives when the
 * device has a network is the wrong dependency for it.
 */
val Sora = FontFamily(
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
)

val PlexSans = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
)

val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
)

/**
 * Trims the extra leading Compose adds above the first line and below the
 * last. Without it a heading in a Row never optically centres against the
 * icon beside it, and every card looks slightly top-heavy.
 */
private val TrimEdges = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun sora(size: Int, lineHeight: Int, tracking: Double, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(
        fontFamily = Sora,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
        lineHeightStyle = TrimEdges,
    )

private fun sans(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = PlexSans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
        lineHeightStyle = TrimEdges,
    )

val Typography = Typography(
    // Sora, tracked in by -0.02em as the design specifies.
    displayLarge = sora(38, 44, -0.76),
    displayMedium = sora(34, 40, -0.68),
    displaySmall = sora(30, 36, -0.75),
    headlineLarge = sora(28, 34, -0.56),
    headlineMedium = sora(25, 31, -0.50),
    headlineSmall = sora(22, 28, -0.44),
    titleLarge = sora(19, 24, -0.19),
    titleMedium = sans(15, 20, FontWeight.SemiBold),
    titleSmall = sans(13, 18, FontWeight.SemiBold),

    bodyLarge = sans(15, 23),
    bodyMedium = sans(13, 19),
    bodySmall = sans(12, 17),

    // Buttons are Sora: they are the one piece of body-sized text that should
    // still feel like a heading.
    labelLarge = sora(15, 20, 0.0),
    labelMedium = sans(12, 16, FontWeight.Medium),
    labelSmall = sans(11, 15, FontWeight.Medium),
)

/**
 * The monospaced styles, which Material's scale has no slot for. Reached
 * through `MedTypography` rather than `MaterialTheme.typography` so it is
 * obvious at the call site that this is an app-specific style, not a Material
 * role someone has bent out of shape.
 */
object MedTypography {

    /** Section headers: `NEXT APPOINTMENT`, `YOUR DATA`. Always uppercased. */
    val sectionLabel = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
        lineHeightStyle = TrimEdges,
    )

    /** Field labels inside inputs: `EMAIL`, `PASSWORD`. */
    val fieldLabel = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.9.sp,
        lineHeightStyle = TrimEdges,
    )

    /** Status lines: "All changes synced · 09:12", "3 PENDING". */
    val monoCaption = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp,
        lineHeightStyle = TrimEdges,
    )

    /** Counts and measurements read down a column: sizes, doses, distances. */
    val monoNumber = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        lineHeightStyle = TrimEdges,
    )

    /** Axis ticks and the smallest annotations. */
    val monoMicro = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.7.sp,
        lineHeightStyle = TrimEdges,
    )

    /** The single large figure on the hero card - days away, percentage. */
    val heroFigure = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.6).sp,
        lineHeightStyle = TrimEdges,
    )
}
