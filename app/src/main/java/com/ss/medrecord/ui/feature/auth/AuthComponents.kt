package com.ss.medrecord.ui.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ss.medrecord.ui.components.MedIconButton
import com.ss.medrecord.ui.components.MedIconGlyph
import com.ss.medrecord.ui.components.MedTextField
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedTheme

/**
 * Shared chrome for the four auth screens, so they cannot drift apart in
 * spacing, scroll behaviour or error presentation.
 *
 * Left-aligned rather than centred, and vertically centred in the window: the
 * design treats these as one statement with a form under it, not as a dialog.
 */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MedTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.jade),
                contentAlignment = Alignment.Center,
            ) {
                MedIconGlyph(
                    icon = MedIcons.HealthAndSafety,
                    size = 30.dp,
                    tint = colors.onJade,
                    contentDescription = null,
                )
            }

            Spacer(Modifier.height(26.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(30.dp))
            content()
        }
    }
}

/**
 * The offline reassurance that closes every auth screen.
 *
 * It is here because this is the moment the promise matters: someone signing in
 * on a train wants to know the app is not about to be useless.
 */
@Composable
fun OfflineNote(
    text: String = "Works offline. Records sync when you reconnect.",
    modifier: Modifier = Modifier,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.hairline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MedIconGlyph(
            icon = MedIcons.WifiOff,
            size = 18.dp,
            tint = colors.textTertiary,
            contentDescription = null,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
) {
    MedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        error = errorMessage,
        enabled = enabled,
        keyboardType = keyboardType,
        imeAction = imeAction,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
) {
    var revealed by remember { mutableStateOf(false) }
    MedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        error = errorMessage,
        enabled = enabled,
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailing = {
            MedIconButton(
                icon = if (revealed) MedIcons.VisibilityOff else MedIcons.Visibility,
                onClick = { revealed = !revealed },
                contentDescription = if (revealed) "Hide password" else "Show password",
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}
