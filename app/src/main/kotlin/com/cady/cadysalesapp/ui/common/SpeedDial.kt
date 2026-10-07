package com.cady.cadysalesapp.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

@Immutable
class SpeedDialItem(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val onClick: () -> Unit,
)

/**
 * A "+" button that fans out into labelled mini buttons — one tap to the thing wanted instead of a
 * dropdown menu hanging off the corner. The + turns into a ✕ while open; Back closes it.
 */
@Composable
fun SpeedDialFab(
    items: List<SpeedDialItem>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 135f else 0f, label = "speedDialRotation")
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    BackHandler(enabled = expanded) { expanded = false }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = scheme.inverseSurface, shadowElevation = 2.dp) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            expanded = false
                            item.onClick()
                        },
                        containerColor = item.container,
                        contentColor = Color.White,
                    ) {
                        Icon(item.icon, contentDescription = item.label)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                expanded = !expanded
            },
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "إضافة مستند", modifier = Modifier.rotate(rotation))
        }
    }
}
