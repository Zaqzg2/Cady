package com.cady.cadysalesapp.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

/**
 * Records strokes as raw point lists in the pad's own pixel space, so the same
 * data both draws live (via Compose's Canvas below) and rasterizes to a real
 * PNG file (via plain android.graphics — no graphicsLayer capture API needed,
 * which keeps this independent of the exact Compose UI version's capture
 * support and easy to verify without a real device).
 */
class SignaturePadState {
    val strokes: SnapshotStateList<SnapshotStateList<Offset>> = mutableStateListOf()
    var canvasSize by mutableStateOf(IntSize.Zero)

    val isEmpty: Boolean get() = strokes.isEmpty()

    fun startStroke(point: Offset) {
        strokes.add(mutableStateListOf(point))
    }

    fun extendLastStroke(point: Offset) {
        strokes.lastOrNull()?.add(point)
    }

    fun clear() {
        strokes.clear()
    }

    /** Renders the recorded strokes into a PNG at [file], sized to match the pad
        exactly as drawn. Returns false (and writes nothing) if the pad is empty. */
    fun saveTo(file: File): Boolean {
        if (isEmpty || canvasSize.width == 0 || canvasSize.height == 0) return false
        val bitmap = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        strokes.forEach { points ->
            if (points.size < 2) return@forEach
            val path = android.graphics.Path()
            path.moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
            canvas.drawPath(path, paint)
        }
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return true
    }
}

@Composable
fun rememberSignaturePadState(): SignaturePadState = remember { SignaturePadState() }

/** A blank-paper drawing surface for a wet-ink-style signature. Caller is
    responsible for calling `state.saveTo(file)` (e.g. from a "حفظ" button)
    and for offering a `state.clear()` action. */
@Composable
fun SignaturePad(
    state: SignaturePadState,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .onSizeChanged { state.canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> state.startStroke(offset) },
                    onDrag = { change, _ ->
                        change.consume()
                        state.extendLastStroke(change.position)
                    },
                )
            }
    ) {
        state.strokes.forEach { points ->
            if (points.size < 2) return@forEach
            val path = Path()
            path.moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
