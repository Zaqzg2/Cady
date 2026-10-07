package com.cady.cadysalesapp.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * A number that counts up from zero when it first appears and glides from the old value to the new one
 * whenever it changes — the dashboard's totals use it so a period switch is felt, not just redrawn.
 *
 * It animates a 0..1 progress and blends two Doubles with it (a Float would lose digits on big money
 * amounts), so the final frame is always exactly [value].
 */
@Composable
fun animatedNumber(value: Double, durationMillis: Int = 750): Double {
    var from by remember { mutableDoubleStateOf(0.0) }
    var to by remember { mutableDoubleStateOf(value) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(value) {
        val current = from + (to - from) * progress.value
        from = current
        to = value
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
    }
    return from + (to - from) * progress.value
}

@Composable
fun animatedMoneyText(value: Double): String = formatMoney(animatedNumber(value))

@Composable
fun animatedCountText(value: Int): String = animatedNumber(value.toDouble()).let { Math.round(it).toString() }
