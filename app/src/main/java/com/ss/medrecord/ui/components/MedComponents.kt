package com.ss.medrecord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ss.medrecord.ui.theme.MaterialSymbols
import com.ss.medrecord.ui.theme.MedIcon
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedTheme
import com.ss.medrecord.ui.theme.MedTypography

/**
 * The vocabulary every screen is built from.
 *
 * The point of putting these here is that a card, a section label or a status
 * pill can only look one way in this app. When the design moves, one of these
 * functions changes and every screen follows; when a screen needs something
 * these cannot express, that is the signal to add a component rather than to
 * hand-roll a Box with a border on one screen only.
 *
 * None of it knows anything about the domain. A component takes strings,
 * colours and lambdas - never a `Visit` or a `UiState` - so the ui layer's
 * dependency on `domain` stays where the ViewModels put it.
 */

// ---------------------------------------------------------------------------
// Icons
// ---------------------------------------------------------------------------

/**
 * Draws one glyph from the bundled Material Symbols font.
 *
 * Semantics are set explicitly rather than left to the text node: an icon font
 * renders private-use codepoints, so the default announcement would be
 * gibberish. Passing `contentDescription = null` clears semantics entirely,
 * which is right for an icon sitting beside a label that already says the same
 * thing.
 */
@Composable
fun MedIconGlyph(
    icon: MedIcon,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MedTheme.colors.textSecondary,
    contentDescription: String? = icon.label,
) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Text(
        text = icon.glyph,
        style = TextStyle(
            fontFamily = MaterialSymbols,
            fontSize = fontSize,
            // A little over the em box, or round glyphs get their top and
            // bottom shaved off at small sizes; the trim below takes the extra
            // space back so the icon still measures as tall as it looks.
            lineHeight = fontSize * 1.25f,
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
            ),
            // The name only becomes a glyph if the shaper applies the font's
            // ligature table; without this an icon renders as the word
            // "warning" laid across the card.
            fontFeatureSettings = "liga",
        ),
        color = tint,
        modifier = modifier.then(
            if (contentDescription == null) {
                Modifier.clearAndSetSemantics { }
            } else {
                val announced = contentDescription
                Modifier.clearAndSetSemantics { this.contentDescription = announced }
            },
        ),
    )
}

/** A round tap target for a bare icon - back arrows, overflow, close. */
@Composable
fun MedIconButton(
    icon: MedIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = icon.label,
    tint: Color = MedTheme.colors.textSecondary,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MedIconGlyph(
            icon = icon,
            size = 22.dp,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            contentDescription = contentDescription,
        )
    }
}

/**
 * The rounded square that sits at the head of a list row, tinted by what the
 * row is about. It is the fastest way to tell a report row from a visit row
 * without reading either.
 */
@Composable
fun IconTile(
    icon: MedIcon,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        MedIconGlyph(
            icon = icon,
            size = size * 0.5f,
            tint = accent,
            contentDescription = contentDescription,
        )
    }
}

// ---------------------------------------------------------------------------
// Page frame
// ---------------------------------------------------------------------------

/**
 * The page. Every screen starts here.
 *
 * The radial wash at the top is the one piece of decoration in the system: it
 * lifts the header off the flat canvas without adding a bar, which is what
 * lets the screens feel layered while still being a single scrolling column.
 */
@Composable
fun MedScreen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    glow: Color? = MedTheme.colors.jade,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = MedTheme.colors
    val glowAlpha = if (colors.isDark) 0.20f else 0.14f
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .then(
                if (glow == null) {
                    Modifier
                } else {
                    // Drawn here rather than as a Box with a gradient background
                    // because the ellipse is anchored to the top edge, and its
                    // centre is only known once the page has been measured.
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glow.copy(alpha = glowAlpha), Color.Transparent),
                                center = Offset(size.width / 2f, 0f),
                                radius = size.width * 0.95f,
                            ),
                            radius = size.width * 0.95f,
                            center = Offset(size.width / 2f, 0f),
                        )
                    }
                },
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            // Nullable rather than always-on: a screen that emits no messages
            // should not carry a host, and a screen that does emit them must
            // not be able to forget one. Every ShowMessage effect in the app
            // lands here.
            snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
            content = content,
        )
    }
}

/**
 * The header. Sora title, a back arrow when there is somewhere to go back to,
 * and no fill - the page's own glow shows through.
 */
// TopAppBar itself is still experimental. The opt-in is kept inside this
// function - and no experimental type appears in its signature - so that every
// screen can use it without repeating the annotation.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = MedTheme.colors
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                MedIconButton(
                    icon = MedIcons.ArrowBack,
                    onClick = onBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = colors.canvas,
            titleContentColor = colors.textPrimary,
        ),
    )
}

// ---------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------

/**
 * The default container: 20dp corners, one hairline border, no shadow.
 *
 * Elevation in this design comes from the border and the surface being a step
 * lighter than the page, not from a drop shadow. Shadows on a near-black
 * canvas are invisible, and on paper they would fight the flat accents.
 */
@Composable
fun MedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    containerColor: Color = MedTheme.colors.card,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MedTheme.colors
    val border = accent?.copy(alpha = if (colors.isDark) 0.28f else 0.35f) ?: colors.hairline
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The one card allowed a gradient, reserved for the single most important
 * thing on a screen - the next appointment, today's dose count.
 *
 * In dark it is a deep teal wash with jade figures on top; in light the accent
 * carries the whole card and the type goes white. Same card, inverted, because
 * a dark gradient on paper looks like a mistake.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    gradient: List<Color> = MedTheme.colors.heroGradient,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(gradient))
            .then(
                if (colors.isDark) {
                    Modifier.border(1.dp, colors.jade.copy(alpha = 0.22f), shape)
                } else {
                    Modifier
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * A notice that is not an error: exact-alarm permission, an offline hint, a
 * privacy note. [accent] carries the weight - coral only when the user has a
 * decision to make.
 */
@Composable
fun NoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: MedIcon = MedIcons.Info,
    accent: Color = MedTheme.colors.amber,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = if (colors.isDark) 0.12f else 0.10f))
            .border(1.dp, accent.copy(alpha = 0.30f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MedIconGlyph(icon = icon, size = 20.dp, tint = accent, contentDescription = null)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            if (action != null && onAction != null) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(onClick = onAction),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------

/** `NEXT APPOINTMENT`, `YOUR DATA`. Uppercased here so call sites cannot forget. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MedTheme.colors.textTertiary,
) {
    Text(
        text = text.uppercase(),
        style = MedTypography.sectionLabel,
        color = color,
        modifier = modifier,
    )
}

/** A section header with the label on the left and a count or link on the right. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    val colors = MedTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MedTypography.monoCaption,
                color = if (onTrailingClick != null) colors.jade else colors.textSecondary,
                modifier = if (onTrailingClick != null) {
                    Modifier.clickable(onClick = onTrailingClick)
                } else {
                    Modifier
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

/** Filled, full width, Sora label. One per screen: the thing to do next. */
@Composable
fun MedPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: MedIcon? = null,
    container: Color = MedTheme.colors.jade,
    onContainer: Color = MedTheme.colors.onJade,
) {
    val colors = MedTheme.colors
    val active = enabled && !loading
    // Disabled is a flat neutral, not a faded accent. A 30%-opacity jade with
    // white type on it is unreadable in light, and reads as "loading" rather
    // than "not yet" in dark.
    val background by animateColorAsState(
        targetValue = if (active) container else colors.cardHighest,
        label = "primaryButtonBackground",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(enabled = active, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = onContainer,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) onContainer else colors.textTertiary,
            )
            if (icon != null) {
                Spacer(Modifier.width(8.dp))
                MedIconGlyph(
                    icon = icon,
                    size = 19.dp,
                    tint = if (active) onContainer else colors.textTertiary,
                    contentDescription = null,
                )
            }
        }
    }
}

/** Outlined, full width. The alternative to the primary action, not a lesser one. */
@Composable
fun MedOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: MedIcon? = null,
    iconTint: Color = MedTheme.colors.jadeSoft,
    contentColor: Color = MedTheme.colors.textPrimary,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .border(1.dp, colors.hairlineStrong, shape)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            MedIconGlyph(icon = icon, size = 20.dp, tint = iconTint, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
        )
    }
}

/** The compact filled button that lives inside a card. */
@Composable
fun MedTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: MedIcon? = null,
    enabled: Boolean = true,
    container: Color = MedTheme.colors.jade,
    onContainer: Color = MedTheme.colors.onJade,
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) container else container.copy(alpha = 0.3f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            MedIconGlyph(icon = icon, size = 17.dp, tint = onContainer, contentDescription = null)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = onContainer,
        )
    }
}

/** A bordered square button beside a tonal one - directions, share, retry. */
@Composable
fun MedGhostIconButton(
    icon: MedIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = icon.label,
    enabled: Boolean = true,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 38.dp)
            .clip(shape)
            .border(1.dp, colors.hairlineStrong, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MedIconGlyph(
            icon = icon,
            size = 19.dp,
            tint = colors.textSecondary,
            contentDescription = contentDescription,
        )
    }
}

// ---------------------------------------------------------------------------
// Chips and pills
// ---------------------------------------------------------------------------

/**
 * A status word: `3 PENDING`, `UPLOAD FAILED`, `PAUSED`.
 *
 * Mono and uppercase, so it reads as machine state rather than as prose the
 * user is meant to weigh.
 */
@Composable
fun StatusPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text = text.uppercase(),
        style = MedTypography.monoMicro,
        color = if (filled) MedTheme.colors.canvas else accent,
        modifier = modifier
            .clip(shape)
            .background(if (filled) accent else accent.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** The horizontal filter row: All / Visits / Reports / Meds. */
@Composable
fun MedFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: MedIcon? = null,
    accent: Color = MedTheme.colors.jade,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.40f) else colors.hairline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            MedIconGlyph(
                icon = icon,
                size = 16.dp,
                tint = if (selected) accent else colors.textSecondary,
                contentDescription = null,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.textPrimary else colors.textSecondary,
        )
    }
}

/**
 * Initials in a circle. Used for the patient chips and the switcher.
 *
 * The colour is chosen from the patient id, not from a counter, so the same
 * person keeps the same colour across every screen and every launch.
 */
@Composable
fun AvatarInitials(
    name: String,
    modifier: Modifier = Modifier,
    seed: String = name,
    size: Dp = 26.dp,
    accent: Color? = null,
) {
    val colors = MedTheme.colors
    val palette = listOf(colors.jade, colors.azure, colors.violet, colors.amber)
    val background = accent ?: palette[(seed.hashCode().mod(palette.size))]
    val ink = when (background) {
        colors.azure -> colors.onAzure
        colors.violet -> colors.onViolet
        colors.amber -> colors.onAmber
        else -> colors.onJade
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp,
            color = ink,
        )
    }
}

/** "Asha Rao" -> "AR"; "Asha" -> "AS". Never more than two letters. */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

/** The patient selector chip on the dashboard. */
@Composable
fun PatientChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    seed: String = name,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.jade.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) colors.jade.copy(alpha = 0.35f) else colors.hairline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AvatarInitials(name = name, seed = seed, size = 26.dp)
        Text(
            text = name.substringBefore(' '),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.textPrimary else colors.textSecondary,
        )
    }
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

/**
 * The list row this app repeats everywhere: tinted icon tile, title, one line
 * of supporting text, optional trailing status, optional chevron.
 */
@Composable
fun MedListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: MedIcon? = null,
    accent: Color = MedTheme.colors.jade,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = MedTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            IconTile(icon = icon, accent = accent)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
        if (showChevron) {
            MedIconGlyph(
                icon = MedIcons.ChevronRight,
                size = 20.dp,
                tint = colors.textTertiary,
                contentDescription = null,
            )
        }
    }
}

/** A row of key and value, as used down the visit detail sheet. */
@Composable
fun MedDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = MedTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel(label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
        )
    }
}

// ---------------------------------------------------------------------------
// Input
// ---------------------------------------------------------------------------

/**
 * The design's text field: a filled card with the label set small and
 * monospaced *inside* it, above the value.
 *
 * Written on BasicTextField rather than OutlinedTextField because Material's
 * field owns its own label animation, container and 56dp minimum - all three
 * of which would have to be fought to arrive here.
 */
@Composable
fun MedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    val colors = MedTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // For fields that show something while they have focus - the facility
    // field's suggestion list - and have to put it away when they lose it.
    if (onFocusChange != null) {
        LaunchedEffect(focused) { onFocusChange(focused) }
    }

    MedFieldFrame(
        label = label,
        modifier = modifier,
        error = error,
        active = focused,
        trailing = trailing,
    ) {
        Box {
            if (value.isEmpty() && placeholder != null) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textTertiary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (enabled) colors.textPrimary else colors.textSecondary,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.jade),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The shell every field in the app is drawn in: filled card, hairline border,
 * the label set small and monospaced *inside* it, above the value.
 *
 * Extracted because there are three kinds of field - typed, tapped and chosen
 * from a list - and they have to be indistinguishable until you touch them.
 * Three separate implementations of the same rectangle is how a form ends up
 * with two slightly different greys in it.
 *
 * [active] is focus for a text field and "menu is open" for a dropdown; both
 * mean the same thing to the person looking at it, so both get the jade edge.
 */
@Composable
private fun MedFieldFrame(
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    active: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val borderColor = when {
        error != null -> colors.coral
        active -> colors.jade.copy(alpha = 0.45f)
        else -> colors.hairline
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.card)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MedTypography.fieldLabel,
                    color = when {
                        error != null -> colors.coral
                        active -> colors.jadeSoft
                        else -> colors.textTertiary
                    },
                )
                Spacer(Modifier.height(4.dp))
                content()
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.coral,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }
    }
}

/**
 * A field you tap rather than type into - a date, a facility, a time.
 *
 * It looks exactly like [MedTextField] and deliberately so: what changes when
 * you touch it is that something opens, not that the field turns out to have
 * been a button all along. [placeholder] is what stands in when nothing is
 * chosen, and it says what would be there rather than sitting empty.
 */
@Composable
fun MedSelectField(
    value: String?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Not set",
    error: String? = null,
    enabled: Boolean = true,
    icon: MedIcon = MedIcons.ChevronRight,
) {
    val colors = MedTheme.colors
    MedFieldFrame(
        label = label,
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        error = error,
        trailing = {
            MedIconGlyph(
                icon = icon,
                size = 20.dp,
                tint = if (enabled) colors.textSecondary else colors.textTertiary,
                contentDescription = null,
            )
        },
    ) {
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                !enabled -> colors.textTertiary
                value.isNullOrBlank() -> colors.textTertiary
                else -> colors.textPrimary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A field that opens a list of choices anchored to itself.
 *
 * Built on Material's [ExposedDropdownMenuBox] rather than a bare popup,
 * because that is what gets the menu width, placement and dismiss behaviour
 * right against the field it belongs to - only the field itself is ours.
 *
 * [emptyOption] adds an explicit way back to "not recorded". Optional fields
 * need one: without it a mis-tap on a blood group is permanent, which is a
 * poor property for a record somebody may act on in an emergency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MedDropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelected: (T?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    placeholder: String = "Choose",
    emptyOption: String? = null,
) {
    val colors = MedTheme.colors
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        MedFieldFrame(
            label = label,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            error = error,
            active = expanded,
            trailing = {
                MedIconGlyph(
                    icon = if (expanded) MedIcons.ExpandLess else MedIcons.ExpandMore,
                    size = 20.dp,
                    tint = if (enabled) colors.textSecondary else colors.textTertiary,
                    contentDescription = null,
                )
            },
        ) {
            val text = selected?.let(optionLabel)
            Text(
                text = text ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (text == null || !enabled) colors.textTertiary else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.cardRaised,
        ) {
            if (emptyOption != null) {
                MedDropdownItem(
                    text = emptyOption,
                    selected = selected == null,
                    onClick = {
                        onSelected(null)
                        expanded = false
                    },
                )
            }
            options.forEach { option ->
                MedDropdownItem(
                    text = optionLabel(option),
                    selected = option == selected,
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** One row of a dropdown, with a tick against the current choice. */
@Composable
private fun MedDropdownItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MedTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) colors.jade else colors.textPrimary,
            )
        },
        trailingIcon = if (selected) {
            {
                MedIconGlyph(
                    icon = MedIcons.Check,
                    size = 18.dp,
                    tint = colors.jade,
                    contentDescription = null,
                )
            }
        } else {
            null
        },
        onClick = onClick,
    )
}

/**
 * The search field at the head of a list.
 *
 * Single line, a magnifier that is decoration rather than a button, and a
 * clear affordance that only exists once there is something to clear.
 */
@Composable
fun MedSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.hairline, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedIconGlyph(
            icon = MedIcons.Search,
            size = 20.dp,
            tint = colors.textTertiary,
            contentDescription = null,
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textTertiary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.jade),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            MedIconGlyph(
                icon = MedIcons.Close,
                size = 20.dp,
                tint = colors.textSecondary,
                contentDescription = "Clear search",
                modifier = Modifier.clickable { onValueChange("") },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Data figures
// ---------------------------------------------------------------------------

/**
 * The dose ring: a stroked arc showing taken against scheduled, with the
 * fraction in the middle.
 *
 * Drawn rather than approximated with a progress indicator because the design
 * uses a butt cap and a full-width track, and because the figure inside has to
 * sit on the ring's own centre.
 */
@Composable
fun AdherenceRing(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 62.dp,
    accent: Color = MedTheme.colors.violet,
    trackColor: Color = MedTheme.colors.hairlineStrong,
    strokeWidth: Dp = 8.dp,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            if (safeProgress > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * safeProgress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }
        }
        Text(
            text = label,
            style = MedTypography.monoNumber,
            color = MedTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The visits-per-month bars.
 *
 * Values are normalised against the largest bar, not against a fixed ceiling:
 * the shape of a year is what this is for, and one busy month should not
 * flatten the other eleven into nothing.
 *
 * [filledThrough] is how many bars represent time that has actually happened.
 * The months still ahead are drawn in the track colour rather than the accent,
 * because a bright bar at zero reads as "no visits this November" when the
 * truth is that November has not occurred.
 */
@Composable
fun SparklineBars(
    values: List<Int>,
    modifier: Modifier = Modifier,
    accent: Color = MedTheme.colors.jade,
    barHeight: Dp = 54.dp,
    filledThrough: Int = values.size,
) {
    val colors = MedTheme.colors
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = modifier.height(barHeight),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            // A floor rather than zero height, so an empty month is still a
            // visible tick on the axis instead of a gap.
            val fraction = (value.toFloat() / max).coerceAtLeast(0.06f)
            val elapsed = index < filledThrough
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            !elapsed -> colors.hairline
                            value == 0 -> colors.hairlineStrong
                            else -> accent
                        },
                    ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Empty and loading states
// ---------------------------------------------------------------------------

/**
 * What a screen says when it has nothing.
 *
 * Always a sentence about what would put something here, never just "No data"
 * - an empty list in a records app is usually a prompt, not a failure.
 */
@Composable
fun MedEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: MedIcon = MedIcons.FolderOpen,
    accent: Color = MedTheme.colors.jade,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MedTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon = icon, accent = accent, size = 56.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            MedTonalButton(text = actionText, onClick = onAction, icon = MedIcons.Add)
        }
    }
}

/** Centred spinner for a screen that has nothing to show yet. */
@Composable
fun MedLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MedTheme.colors.jade, strokeWidth = 3.dp)
    }
}

// ---------------------------------------------------------------------------
// Bar furniture
// ---------------------------------------------------------------------------

/**
 * A text action in a top bar - "Clear", "Archived", "By clinic".
 *
 * Plain text rather than a Material TextButton, because a filled or bordered
 * button in the header competes with the screen's real primary action. These
 * are switches of view, not things to do.
 */
@Composable
fun MedBarAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = accent ?: MedTheme.colors.jade,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * The pill-shaped add button that floats over a list.
 *
 * Extended rather than icon-only: on a screen with one obvious action, the
 * word costs nothing and removes the guess about what the plus adds.
 */
@Composable
fun MedFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: MedIcon = MedIcons.Add,
    accent: Color = MedTheme.colors.jade,
    onAccent: Color = MedTheme.colors.onJade,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MedIconGlyph(icon = icon, size = 22.dp, tint = onAccent, contentDescription = null)
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = onAccent,
        )
    }
}

/**
 * Design colours for Material's date picker.
 *
 * The picker itself stays Material's - a calendar grid with year selection,
 * range limits and locale-correct week starts is not something to reimplement
 * for the sake of a border radius. Only the palette is ours, so the dialog
 * that opens from a [MedSelectField] does not arrive in a different app's
 * colours.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun medDatePickerColors(): DatePickerColors {
    val colors = MedTheme.colors
    return DatePickerDefaults.colors(
        containerColor = colors.card,
        titleContentColor = colors.textSecondary,
        headlineContentColor = colors.textPrimary,
        weekdayContentColor = colors.textTertiary,
        subheadContentColor = colors.textSecondary,
        navigationContentColor = colors.textSecondary,
        yearContentColor = colors.textPrimary,
        currentYearContentColor = colors.jade,
        selectedYearContentColor = colors.onJade,
        selectedYearContainerColor = colors.jade,
        dayContentColor = colors.textPrimary,
        disabledDayContentColor = colors.textTertiary,
        selectedDayContentColor = colors.onJade,
        selectedDayContainerColor = colors.jade,
        todayContentColor = colors.jade,
        todayDateBorderColor = colors.jade,
        dividerColor = colors.hairline,
    )
}

/** The same treatment for the dose-time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun medTimePickerColors(): TimePickerColors {
    val colors = MedTheme.colors
    return TimePickerDefaults.colors(
        clockDialColor = colors.cardHighest,
        clockDialSelectedContentColor = colors.onJade,
        clockDialUnselectedContentColor = colors.textPrimary,
        selectorColor = colors.jade,
        periodSelectorBorderColor = colors.hairlineStrong,
        periodSelectorSelectedContainerColor = colors.jade.copy(alpha = 0.16f),
        periodSelectorSelectedContentColor = colors.jade,
        periodSelectorUnselectedContentColor = colors.textSecondary,
        timeSelectorSelectedContainerColor = colors.jade.copy(alpha = 0.16f),
        timeSelectorSelectedContentColor = colors.jade,
        timeSelectorUnselectedContainerColor = colors.cardHighest,
        timeSelectorUnselectedContentColor = colors.textPrimary,
    )
}
