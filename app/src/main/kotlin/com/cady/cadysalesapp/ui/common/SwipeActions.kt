package com.cady.cadysalesapp.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.min

/** Fixed, saturated colours for swipe buttons: white labels stay readable in light and dark mode alike. */
object SwipeColors {
    val Blue = Color(0xFF1565C0)
    val Amber = Color(0xFFB26A00)
    val Slate = Color(0xFF546E7A)
    val Red = Color(0xFFC62828)
    val Green = Color(0xFF1B8A5A)
    val Teal = Color(0xFF00796B)
}

/** One button revealed by swiping a row: an icon, a one-word label and what it does. */
@Immutable
class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color = Color.White,
    val onClick: () -> Unit,
)

/** Lets a list keep only one row open at a time: opening one closes whichever other was open. */
@Stable
class SwipeCoordinator {
    var openKey: Any? by mutableStateOf(null)
}

@Composable
fun rememberSwipeCoordinator(): SwipeCoordinator = remember { SwipeCoordinator() }

/**
 * A row you can drag sideways to uncover buttons behind it.
 *
 *  - [rightActions] are uncovered by swiping LEFT (the buttons sit at the row's right edge);
 *  - [leftActions]  are uncovered by swiping RIGHT (the buttons sit at the row's left edge).
 *
 * Both lists are ordered inner → outer: the LAST action in a list is the one at the screen edge, which is
 * where the destructive one (delete) belongs. A drag past 40% of the buttons' width snaps the row open
 * (with a light haptic tick), anything less springs back; tapping a button runs it and closes the row,
 * tapping the row while open only closes it. [peek] nudges the row once to hint that it can be swiped.
 *
 * The row must draw an opaque background of its own (all of the app's rows do) — the buttons sit behind it.
 */
@Composable
fun SwipeActionRow(
    modifier: Modifier = Modifier,
    rowKey: Any = Unit,
    coordinator: SwipeCoordinator? = null,
    leftActions: List<SwipeAction> = emptyList(),
    rightActions: List<SwipeAction> = emptyList(),
    enabled: Boolean = true,
    peek: Boolean = false,
    shape: Shape = RoundedCornerShape(20.dp),
    actionWidth: Dp = 72.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val actionPx = with(density) { actionWidth.toPx() }
    val leftWidth = actionPx * leftActions.size
    val rightWidth = actionPx * rightActions.size
    val hasActions = leftActions.isNotEmpty() || rightActions.isNotEmpty()

    var offset by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var wasPastThreshold by remember { mutableStateOf(false) }

    val latestLeft by rememberUpdatedState(leftWidth)
    val latestRight by rememberUpdatedState(rightWidth)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestCoordinator by rememberUpdatedState(coordinator)
    val latestKey by rememberUpdatedState(rowKey)

    fun settle(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = offset,
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> offset = value }
        }
    }

    fun close() {
        settle(0f)
        if (latestCoordinator?.openKey == latestKey) latestCoordinator?.openKey = null
    }

    fun finishDrag() {
        val left = latestLeft
        val right = latestRight
        val target = when {
            left > 0f && offset > left * 0.4f -> left
            right > 0f && offset < -right * 0.4f -> -right
            else -> 0f
        }
        settle(target)
        if (target != 0f) latestCoordinator?.openKey = latestKey
    }

    // Another row opened: this one folds back.
    val openKey = coordinator?.openKey
    LaunchedEffect(openKey) {
        if (coordinator != null && openKey != rowKey && offset != 0f) settle(0f)
    }

    // One small nudge so the gesture is discoverable (only the row the caller marks, and only once).
    LaunchedEffect(peek) {
        if (!peek || !hasActions) return@LaunchedEffect
        delay(900)
        if (offset != 0f) return@LaunchedEffect
        val target = if (rightWidth > 0f) -min(rightWidth, actionPx * 0.85f) else min(leftWidth, actionPx * 0.85f)
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(0f, target, animationSpec = tween(380, easing = FastOutSlowInEasing)) { value, _ -> offset = value }
            delay(500)
            animate(offset, 0f, animationSpec = tween(380, easing = FastOutSlowInEasing)) { value, _ -> offset = value }
        }
    }

    val showLeft by remember { derivedStateOf { offset > 0.5f } }
    val showRight by remember { derivedStateOf { offset < -0.5f } }
    val isOpen by remember { derivedStateOf { abs(offset) > 0.5f } }

    // "Last in the list = outermost", whichever way the layout runs.
    val leftOrdered = if (rtl) leftActions else leftActions.asReversed()
    val rightOrdered = if (rtl) rightActions.asReversed() else rightActions

    val dragModifier = if (hasActions) {
        Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = {
                    settleJob?.cancel()
                    wasPastThreshold = false
                },
                onDragEnd = { finishDrag() },
                onDragCancel = { finishDrag() },
                onHorizontalDrag = { change, dragAmount ->
                    if (latestEnabled) {
                        change.consume()
                        offset = (offset + dragAmount).coerceIn(-latestRight, latestLeft)
                        val reach = if (offset > 0f) latestLeft else latestRight
                        val past = reach > 0f && abs(offset) > reach * 0.4f
                        if (past != wasPastThreshold) {
                            wasPastThreshold = past
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                },
            )
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxWidth().clip(shape)) {
        Box(modifier = Modifier.matchParentSize()) {
            if (showLeft && leftActions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(if (rtl) Alignment.CenterEnd else Alignment.CenterStart)
                        .fillMaxHeight(),
                ) {
                    leftOrdered.forEach { action ->
                        SwipeActionCell(action, actionWidth) { close(); action.onClick() }
                    }
                }
            }
            if (showRight && rightActions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(if (rtl) Alignment.CenterStart else Alignment.CenterEnd)
                        .fillMaxHeight(),
                ) {
                    rightOrdered.forEach { action ->
                        SwipeActionCell(action, actionWidth) { close(); action.onClick() }
                    }
                }
            }
        }
        Box(
            modifier = dragModifier.offset { IntOffset(offset.roundToInt(), 0) },
        ) {
            content()
            if (isOpen) {
                // While open the row is a "close" button, not a link.
                Box(modifier = Modifier.matchParentSize().noRippleClickable { close() })
            }
        }
    }
}

@Composable
private fun SwipeActionCell(action: SwipeAction, width: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(action.container)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(action.icon, contentDescription = null, tint = action.content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            action.label,
            color = action.content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
