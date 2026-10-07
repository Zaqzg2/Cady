package com.cady.cadysalesapp.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cady.cadysalesapp.ui.theme.cady

// ------------------------------------------------------------------------------------------------
// Interaction helpers
// ------------------------------------------------------------------------------------------------

/** A small spring "press" on anything tappable: it sinks a few percent while the finger is down. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Tappable without the ripple — for invisible hit areas (a scrim, a chart bar). */
@Composable
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )

/**
 * 0 → [target] the first time it is shown, then glides to each new target. Bars and progress strips use
 * it so a dashboard "fills in" when it opens instead of appearing already full.
 */
@Composable
fun animateGrowIn(target: Float, durationMillis: Int = 700, delayMillis: Int = 0): Float {
    var goal by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(target) { goal = target }
    val value by animateFloatAsState(
        targetValue = goal,
        animationSpec = tween(durationMillis = durationMillis, delayMillis = delayMillis),
        label = "growIn",
    )
    return value
}

// ------------------------------------------------------------------------------------------------
// Containers
// ------------------------------------------------------------------------------------------------

/**
 * The app's card: white on the warm page, hairline border, 22dp corners. Drop-in for Material's
 * `Card(...)` (same `onClick` / `enabled` / `colors` / `modifier` names), so every screen — including the
 * ones not redesigned one by one — gets the same surface. A card with its own `colors` (a tinted
 * summary card) gets no border.
 */
@Composable
fun CadyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: CardColors? = null,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val cardColors = colors ?: CardDefaults.cardColors(
        containerColor = scheme.surfaceContainerLowest,
        contentColor = scheme.onSurface,
        disabledContainerColor = scheme.surfaceContainerLowest,
        disabledContentColor = scheme.onSurface.copy(alpha = 0.6f),
    )
    val border = if (colors == null) BorderStroke(1.dp, scheme.outlineVariant) else null
    if (onClick == null) {
        Card(modifier = modifier, shape = shape, colors = cardColors, border = border, content = content)
    } else {
        val interaction = remember { MutableInteractionSource() }
        Card(
            onClick = onClick,
            modifier = modifier.pressScale(interaction),
            enabled = enabled,
            shape = shape,
            colors = cardColors,
            border = border,
            interactionSource = interaction,
            content = content,
        )
    }
}

/** The soap-bubble decoration of the hero panels and the login header: a few translucent circles behind the content. */
fun Modifier.soapBubbles(): Modifier = this.drawBehind {
    val w = size.width
    val h = size.height
    drawCircle(Color.White.copy(alpha = 0.07f), radius = h * 0.62f, center = Offset(w * 0.06f, h * 0.02f))
    drawCircle(Color.White.copy(alpha = 0.05f), radius = h * 0.42f, center = Offset(w * 0.94f, h * 1.0f))
    drawCircle(Color.White.copy(alpha = 0.08f), radius = h * 0.13f, center = Offset(w * 0.64f, h * 0.2f))
    drawCircle(Color.White.copy(alpha = 0.06f), radius = h * 0.08f, center = Offset(w * 0.8f, h * 0.55f))
}

/**
 * The brand "hero" panel: a deep-to-bright gradient of the brand colour with a few translucent soap
 * bubbles drawn into it (cleaning products — the one decorative motif that belongs to this business).
 * Text and icons inside default to the panel's own on-colour.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extra = MaterialTheme.cady
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd)))
            .soapBubbles()
    ) {
        CompositionLocalProvider(LocalContentColor provides extra.onHero) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), content = content)
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Top bar
// ------------------------------------------------------------------------------------------------

/**
 * Drop-in for Material's `TopAppBar`: same page colour as the screen (no tinted bar floating over it),
 * bold title. Every screen's bar goes through here so they cannot drift apart again.
 */
@Composable
fun CadyAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = scheme.background,
            scrolledContainerColor = scheme.background,
            titleContentColor = scheme.onSurface,
            navigationIconContentColor = scheme.onSurface,
            actionIconContentColor = scheme.onSurfaceVariant,
        ),
    )
}

/** The back arrow (mirrored automatically in the app's right-to-left layout). */
@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
    }
}

// ------------------------------------------------------------------------------------------------
// Small pieces
// ------------------------------------------------------------------------------------------------

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (trailing != null) trailing()
    }
}

/** A small rounded label: "نقد", "المندوب: أحمد", "ساري الآن". */
@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    container: Color = color.copy(alpha = 0.14f),
    icon: ImageVector? = null,
) {
    Surface(shape = RoundedCornerShape(50), color = container, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = color)
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** An icon on a tinted rounded square — the leading mark of a row, a tile or a setting. */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cornerRadius: Dp = 14.dp,
) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(cornerRadius)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.52f), tint = tint)
    }
}

/** An empty screen is an invitation: what is missing, and (optionally) the button that fixes it. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** A labelled number on a card: the building block of the dashboard and the manager's overview. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    footer: (@Composable () -> Unit)? = null,
) {
    CadyCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconTile(icon = icon, tint = tint, container = tint.copy(alpha = 0.13f), size = 32.dp, cornerRadius = 10.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                maxLines = 1,
            )
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (footer != null) footer()
        }
    }
}

/** A thin rounded progress strip that fills in when it first appears. */
@Composable
fun ThinProgress(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val shown = animateGrowIn(fraction.coerceIn(0f, 1f))
    Box(modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(shown)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

// ------------------------------------------------------------------------------------------------
// Inputs
// ------------------------------------------------------------------------------------------------

/** A two-or-three-way switch drawn as one rounded track with a coloured selected segment. */
@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accentFor: ((T) -> Color)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val accent = accentFor?.invoke(value) ?: scheme.primary
            val onAccent = if (accent.luminance() > 0.55f) Color(0xFF1A1110) else Color.White
            val segmentFill by animateColorAsState(
                targetValue = if (isSelected) accent else Color.Transparent,
                label = "segmentFill",
            )
            val segmentText by animateColorAsState(
                targetValue = if (isSelected) onAccent else scheme.onSurfaceVariant,
                label = "segmentText",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(segmentFill)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = segmentText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The search box used by every list: a rounded pill with a clear button once something is typed. */
@Composable
fun CadySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
                }
            }
        } else {
            null
        },
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerLowest,
            unfocusedContainerColor = scheme.surfaceContainerLowest,
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outlineVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

// ------------------------------------------------------------------------------------------------
// Avatars
// ------------------------------------------------------------------------------------------------

private val avatarPalette = listOf(
    Color(0xFF1B8A5A), Color(0xFF1565C0), Color(0xFFC77700), Color(0xFF8E24AA),
    Color(0xFF00838F), Color(0xFFD81B60), Color(0xFF5D4037), Color(0xFF3949AB),
)

/** A round tile with the first letter of [name], in a colour picked from the name so it is stable. */
@Composable
fun AvatarInitial(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    val base = avatarPalette[(name.hashCode() and 0x7fffffff) % avatarPalette.size]
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.toString() ?: "؟"
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(base.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = base, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}
