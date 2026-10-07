package com.cady.cadysalesapp.ui.reports

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cady.cadysalesapp.ui.common.ThinProgress
import com.cady.cadysalesapp.ui.common.animateGrowIn
import com.cady.cadysalesapp.ui.common.formatMoney
import com.cady.cadysalesapp.ui.common.noRippleClickable
import com.cady.cadysalesapp.ui.theme.cady
import java.util.Locale
import kotlin.math.abs

/** (current − previous) / |previous| in percent, or null when there is nothing meaningful to compare with. */
internal fun deltaPercent(current: Double, previous: Double?): Double? {
    if (previous == null) return null
    if (abs(previous) < 0.005) return null
    return (current - previous) / abs(previous) * 100.0
}

private fun percentText(percent: Double): String {
    val a = abs(percent)
    return if (a >= 10.0) String.format(Locale.US, "%.0f", a) else String.format(Locale.US, "%.1f", a)
}

/**
 * "▲ 12%  عن أمس". On the hero it is a translucent white chip; on a tile it is plain coloured text, where
 * green means "better" — which for returns is *down*, hence [higherIsBetter].
 */
@Composable
fun DeltaChip(
    current: Double,
    previous: Double?,
    label: String,
    modifier: Modifier = Modifier,
    onHero: Boolean = false,
    higherIsBetter: Boolean = true,
) {
    val percent = deltaPercent(current, previous) ?: return
    val up = percent >= 0.0
    val good = if (higherIsBetter) up else !up
    val tint = when {
        onHero -> LocalContentColor.current
        good -> MaterialTheme.cady.success
        else -> MaterialTheme.cady.debt
    }
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                percentText(percent) + "%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            if (label.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = tint.copy(alpha = 0.85f))
            }
        }
    }
    if (onHero) {
        Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.16f), modifier = modifier) {
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) { content() }
        }
    } else {
        Box(modifier = modifier) { content() }
    }
}

/**
 * Sales per day (or month) as bars that grow in when the dashboard opens. Tap a bar to select it; the
 * selected one is solid, other bars of the period are softer, and context bars outside the period are grey.
 * Plain layout (no Canvas), so the bars run in the reading direction of the screen: oldest at the right.
 */
@Composable
fun TrendBars(
    points: List<TrendPoint>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val saleColor = MaterialTheme.cady.sale
    val maxValue = remember(points) { (points.maxOfOrNull { it.sales } ?: 0.0).coerceAtLeast(1.0) }
    val gap = if (points.size > 20) 3.dp else 6.dp
    val labelEvery = when {
        points.size <= 12 -> 1
        points.size <= 20 -> 2
        else -> 5
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            points.forEachIndexed { index, point ->
                val fraction = animateGrowIn(
                    target = (point.sales / maxValue).toFloat(),
                    delayMillis = (index * 18).coerceAtMost(360),
                )
                val barColor = when {
                    index == selectedIndex -> saleColor
                    point.inPeriod -> saleColor.copy(alpha = 0.5f)
                    else -> scheme.outlineVariant
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().noRippleClickable { onSelect(index) },
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceIn(0.015f, 1f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(barColor),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            points.forEachIndexed { index, point ->
                Text(
                    text = if (index % labelEvery == 0 || index == points.lastIndex) point.label else "",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == selectedIndex) saleColor else scheme.onSurfaceVariant,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Immutable
class DonutSlice(val value: Double, val color: Color)

/** A ring split into [slices] that sweeps open when first shown; [center] is drawn in the hole. */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp,
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val progress = remember { Animatable(0f) }
    val values = slices.map { it.value }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (total > 0.0) {
                val visible = slices.count { it.value > 0.0 }
                val gapDegrees = if (visible > 1) 4f else 0f
                var start = -90f
                slices.forEach { slice ->
                    if (slice.value > 0.0) {
                        val full = (slice.value / total).toFloat() * 360f
                        val sweep = (full - gapDegrees).coerceAtLeast(0.5f) * progress.value
                        drawArc(
                            color = slice.color,
                            startAngle = start + gapDegrees / 2f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        start += full
                    }
                }
            }
        }
        center()
    }
}

/** A top-N list: rank badge, name, amount, and a strip whose length is the share of the leader. */
@Composable
fun RankedBars(
    items: List<RankedItem>,
    color: Color,
    modifier: Modifier = Modifier,
    valueText: (RankedItem) -> String = { formatMoney(it.value) },
) {
    val maxValue = remember(items) { (items.maxOfOrNull { abs(it.value) } ?: 1.0).coerceAtLeast(1.0) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, item ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (index + 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        valueText(item),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                ThinProgress(fraction = (abs(item.value) / maxValue).toFloat(), color = color, height = 6.dp)
                if (item.caption != null) {
                    Text(
                        item.caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
