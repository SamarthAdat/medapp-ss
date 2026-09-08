package com.ss.medrecord.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tokens the Material scheme has no slot for.
 *
 * Material gives one primary and a handful of containers. This design runs
 * five accents that each mean something specific, three surface levels, and
 * three ink tiers - so those live here, reached through [LocalMedColors],
 * while everything Material *does* model stays in [MaterialTheme.colorScheme].
 * Two sources of colour is one more than ideal, but the alternative is
 * overloading `tertiary` to mean "medicines" and hoping nobody notices.
 */
@Immutable
data class MedColors(
    val jade: Color,
    val jadeSoft: Color,
    val azure: Color,
    val violet: Color,
    val amber: Color,
    val coral: Color,
    val onJade: Color,
    val onAzure: Color,
    val onViolet: Color,
    val onAmber: Color,
    val onCoral: Color,
    val canvas: Color,
    val card: Color,
    val cardRaised: Color,
    val cardHighest: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val heroGradient: List<Color>,
    val onHero: Color,
    val onHeroMuted: Color,
    val heroFigure: Color,
    val isDark: Boolean,
) {
    /** The hero card's wash, top-left to bottom-right as the design draws it. */
    val heroBrush: Brush get() = Brush.linearGradient(heroGradient)

    /**
     * The accent for a record kind. Kept here so a chip on the dashboard and
     * the same chip on the timeline cannot drift apart.
     */
    fun accentFor(kind: MedAccent): Color = when (kind) {
        MedAccent.Visit -> jade
        MedAccent.Report -> azure
        MedAccent.Medicine -> violet
        MedAccent.Pending -> amber
        MedAccent.Attention -> coral
    }

    fun onAccentFor(kind: MedAccent): Color = when (kind) {
        MedAccent.Visit -> onJade
        MedAccent.Report -> onAzure
        MedAccent.Medicine -> onViolet
        MedAccent.Pending -> onAmber
        MedAccent.Attention -> onCoral
    }
}

/** What each accent is allowed to mean. See the palette note in Color.kt. */
enum class MedAccent { Visit, Report, Medicine, Pending, Attention }

private val DarkMedColors = MedColors(
    jade = JadeDark,
    jadeSoft = JadeDarkSoft,
    azure = AzureDark,
    violet = VioletDark,
    amber = AmberDark,
    coral = CoralDarkSoft,
    onJade = OnJadeDark,
    onAzure = OnAzureDark,
    onViolet = OnVioletDark,
    onAmber = OnAmberDark,
    onCoral = OnCoralDark,
    canvas = CanvasDark,
    card = CardDark,
    cardRaised = CardRaisedDark,
    cardHighest = CardHighestDark,
    hairline = HairlineDark,
    hairlineStrong = HairlineStrongDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    heroGradient = listOf(HeroTopDark, HeroMidDark, HeroBottomDark),
    onHero = TextPrimaryDark,
    onHeroMuted = Color(0xFFA9B8BB),
    heroFigure = JadeDark,
    isDark = true,
)

private val LightMedColors = MedColors(
    jade = JadeLight,
    jadeSoft = JadeLightSoft,
    azure = AzureLight,
    violet = VioletLight,
    amber = AmberLight,
    coral = CoralLight,
    onJade = Color.White,
    onAzure = Color.White,
    onViolet = Color.White,
    onAmber = Color.White,
    onCoral = Color.White,
    canvas = CanvasLight,
    card = CardLight,
    cardRaised = CardRaisedLight,
    cardHighest = CardHighestLight,
    hairline = HairlineLight,
    hairlineStrong = HairlineStrongLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    // Light inverts the hero: the accent carries the card and the type is
    // white, rather than dark ink with the accent used for figures.
    heroGradient = listOf(HeroTopLight, HeroMidLight, HeroBottomLight),
    onHero = Color.White,
    onHeroMuted = Color(0xE6FFFFFF),
    heroFigure = Color.White,
    isDark = false,
)

val LocalMedColors = staticCompositionLocalOf { DarkMedColors }

/** `MedTheme.colors.jade` at the call site. */
object MedTheme {
    val colors: MedColors
        @Composable @ReadOnlyComposable get() = LocalMedColors.current
}

private val DarkColors = darkColorScheme(
    primary = JadeDark,
    onPrimary = OnJadeDark,
    primaryContainer = JadeContainerDark,
    onPrimaryContainer = OnJadeContainerDark,
    secondary = AzureDark,
    onSecondary = OnAzureDark,
    secondaryContainer = AzureContainerDark,
    onSecondaryContainer = OnAzureContainerDark,
    tertiary = VioletDark,
    onTertiary = OnVioletDark,
    tertiaryContainer = VioletContainerDark,
    onTertiaryContainer = OnVioletContainerDark,
    error = CoralDarkSoft,
    onError = OnCoralDark,
    errorContainer = CoralContainerDark,
    onErrorContainer = OnCoralContainerDark,
    background = CanvasDark,
    onBackground = TextPrimaryDark,
    surface = CanvasDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = CanvasDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardRaisedDark,
    surfaceContainerHighest = CardHighestDark,
    outline = OutlineDarkTone,
    outlineVariant = Color(0xFF1E262C),
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = CanvasDark,
    inversePrimary = JadeLight,
)

private val LightColors = lightColorScheme(
    primary = JadeLight,
    onPrimary = Color.White,
    primaryContainer = JadeContainerLight,
    onPrimaryContainer = OnJadeContainerLight,
    secondary = AzureLight,
    onSecondary = Color.White,
    secondaryContainer = AzureContainerLight,
    onSecondaryContainer = OnAzureContainerLight,
    tertiary = VioletLight,
    onTertiary = Color.White,
    tertiaryContainer = VioletContainerLight,
    onTertiaryContainer = OnVioletContainerLight,
    error = CoralLight,
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = OnCoralContainerLight,
    background = CanvasLight,
    onBackground = TextPrimaryLight,
    surface = CanvasLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = CardHighestLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = CardHighestLight,
    outline = OutlineLightTone,
    outlineVariant = HairlineLight,
    inverseSurface = TextPrimaryLight,
    inverseOnSurface = CanvasLight,
    inversePrimary = JadeDark,
)

/**
 * Corner radii from the design: pills for chips, 20dp for cards, 16dp for
 * inputs and full-width buttons, 12dp for the small buttons inside a card.
 */
private val MedShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Dynamic colour is deliberately absent rather than off by default.
 *
 * The five accents each carry a meaning - violet is a dose, amber is an
 * unsynced write, coral is a decision the user has to make. Re-deriving them
 * from the device wallpaper would collapse that into one hue family and take
 * the meaning with it.
 */
@Composable
fun MedRecordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val medColors = if (darkTheme) DarkMedColors else LightMedColors

    CompositionLocalProvider(LocalMedColors provides medColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = MedShapes,
            content = content,
        )
    }
}
