package com.ss.medrecord.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette from the "MedRecord Keeper — enhanced" design.
 *
 * The design is authored in oklch, which Compose has no literal for, so every
 * value here is the sRGB conversion of the oklch the design specifies; the
 * original is kept in the comment so a later change can be traced back to it.
 * Hues are constant between light and dark - only lightness moves - which is
 * why the two schemes read as one system rather than two palettes.
 *
 * Five accents, each with a fixed job. Using them for anything else is what
 * turns a palette back into decoration:
 *   jade   - primary, "this is fine / this is the action"
 *   azure  - reports and files
 *   violet - medicines and doses
 *   amber  - pending sync, nothing lost, nothing to do yet
 *   coral  - needs a decision from the user
 */

// ---------------------------------------------------------------------------
// Accents - dark scheme. Lighter, lower chroma: they sit on near-black ink.
// ---------------------------------------------------------------------------
val JadeDark = Color(0xFF47C496)          // oklch(0.74 0.13 165)
val JadeDarkSoft = Color(0xFF6CCEA6)      // oklch(0.78 0.11 165)
val JadeDarkBright = Color(0xFF8AD7B6)    // oklch(0.82 0.09 165)
val AzureDark = Color(0xFF5DB2F7)         // oklch(0.74 0.13 245)
val AzureDarkSoft = Color(0xFF78BFF9)     // oklch(0.78 0.11 245)
val VioletDark = Color(0xFFB897F0)        // oklch(0.74 0.13 300)
val VioletDarkSoft = Color(0xFFC3A5F9)    // oklch(0.78 0.12 300)
val AmberDark = Color(0xFFDA9E3F)         // oklch(0.74 0.13 75)
val AmberDarkSoft = Color(0xFFE4B572)     // oklch(0.80 0.10 75)
val CoralDark = Color(0xFFE3645E)         // oklch(0.66 0.16 25)
val CoralDarkSoft = Color(0xFFF97770)     // oklch(0.72 0.16 25)
val CoralDarkBright = Color(0xFFFFB7B0)   // oklch(0.85 0.09 25)

// Ink used *on top of* a full-strength accent fill. Near-black rather than
// pure black so a filled button does not vibrate against the page.
val OnJadeDark = Color(0xFF071410)
val OnAzureDark = Color(0xFF04121C)
val OnVioletDark = Color(0xFF12061C)
val OnAmberDark = Color(0xFF1A1204)
val OnCoralDark = Color(0xFF2A0806)

// ---------------------------------------------------------------------------
// Accents - light scheme. One step darker, so full-opacity type on white
// still clears 4.5:1.
// ---------------------------------------------------------------------------
val JadeLight = Color(0xFF0A9068)         // oklch(0.58 0.12 165)
val JadeLightSoft = Color(0xFF249C74)     // oklch(0.62 0.12 165)
val JadeLightDeep = Color(0xFF007151)     // oklch(0.48 0.11 168)
val JadeLightMid = Color(0xFF007760)      // oklch(0.50 0.11 175)
val AzureLight = Color(0xFF3080BC)        // oklch(0.58 0.12 245)
val VioletLight = Color(0xFF8766BB)       // oklch(0.58 0.13 300)
val VioletLightDeep = Color(0xFF6F4FA1)   // oklch(0.50 0.13 300)
val AmberLight = Color(0xFFB37903)        // oklch(0.62 0.13 75)
val CoralLight = Color(0xFFB63132)        // oklch(0.52 0.17 25)
val CoralLightDeep = Color(0xFF972527)    // oklch(0.45 0.15 25)

// ---------------------------------------------------------------------------
// Surfaces and ink
// ---------------------------------------------------------------------------
// Dark: three levels only - page, card, raised. More than three and elevation
// stops meaning anything.
val CanvasDark = Color(0xFF0B0E12)
val CardDark = Color(0xFF121A20)
val CardRaisedDark = Color(0xFF151D24)
val CardHighestDark = Color(0xFF1B242C)
val HairlineDark = Color(0x12FFFFFF)      // rgba(255,255,255,.07)
val HairlineStrongDark = Color(0x24FFFFFF) // rgba(255,255,255,.14)
val OutlineDarkTone = Color(0xFF46545A)

val TextPrimaryDark = Color(0xFFE8EDEC)
val TextSecondaryDark = Color(0xFF93A1A5)
val TextTertiaryDark = Color(0xFF6D7B7F)
val TextOnAccentSurfaceDark = Color(0xFFC6D4D6)

// The "next appointment" hero, the one card in the app that is allowed a
// gradient. Dark: a deep teal wash that the jade figures sit inside.
val HeroTopDark = Color(0xFF16323A)
val HeroMidDark = Color(0xFF101C22)
val HeroBottomDark = Color(0xFF0E171C)

// Light: paper. White cards float on an off-white page - the inverse of dark,
// where cards are lighter than the page.
val CanvasLight = Color(0xFFF4F6F4)
val CardLight = Color(0xFFFFFFFF)
val CardRaisedLight = Color(0xFFFFFFFF)
val CardHighestLight = Color(0xFFEDF0ED)
val HairlineLight = Color(0xFFE0E5E1)
val HairlineStrongLight = Color(0xFFD6DEDA)
val OutlineLightTone = Color(0xFF98A5A7)

val TextPrimaryLight = Color(0xFF12181A)
val TextSecondaryLight = Color(0xFF5C6B6E)
val TextTertiaryLight = Color(0xFF7C8A8D)

// Light hero: the accent itself carries the card, with white type on top.
val HeroTopLight = JadeLight
val HeroMidLight = Color(0xFF058064)
val HeroBottomLight = JadeLightMid

// ---------------------------------------------------------------------------
// Material container tones, derived from the accents above
// ---------------------------------------------------------------------------
val JadeContainerDark = Color(0xFF16323A)
val OnJadeContainerDark = JadeDarkBright
val AzureContainerDark = Color(0xFF10222E)
val OnAzureContainerDark = Color(0xFF86C5FA)
val VioletContainerDark = Color(0xFF241C36)
val OnVioletContainerDark = VioletDarkSoft
val CoralContainerDark = Color(0xFF2E1513)
val OnCoralContainerDark = CoralDarkBright

val JadeContainerLight = Color(0xFFD9EFE5)
val OnJadeContainerLight = Color(0xFF00382A)
val AzureContainerLight = Color(0xFFD9E8F5)
val OnAzureContainerLight = Color(0xFF0A2C44)
val VioletContainerLight = Color(0xFFE6DDF3)
val OnVioletContainerLight = Color(0xFF2C1B47)
val CoralContainerLight = Color(0xFFF7DEDD)
val OnCoralContainerLight = Color(0xFF4A100F)
