package com.cady.cadysalesapp.ui.common

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/*
 * A signature field that signs on a FULL-SCREEN pad.
 *
 * On the form it is only a small preview box (the saved signature, or a "tap to sign" prompt).
 * Tapping it opens a pad that covers the whole screen — much more room for a finger, in portrait
 * or landscape. "تم" saves the signature as a PNG and hands its path back; "✕"/Back discards.
 *
 * It reuses SignaturePadState (SignaturePad.kt) for the recorded strokes, so the older inline
 * SignaturePad (receipts, Settings) is untouched.
 */

/** Pen width while drawing on the full-screen pad. */
private const val LIVE_PEN_DP = 3f

// The saved image is NOT the pad's size (that would be a whole screen). It is cropped to the ink
// and normalised to this box, because PdfService fits the PNG into a ~50pt-high strip on the
// receipt — a screen-sized image with a small signature in the middle would print as a speck.
private const val EXPORT_MAX_WIDTH = 960f
private const val EXPORT_MAX_HEIGHT = 320f
private const val EXPORT_MARGIN = 14f
private const val EXPORT_PEN = 6f
private const val EXPORT_MAX_UPSCALE = 3f

private fun inkPaint(widthPx: Float): android.graphics.Paint =
    android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = widthPx
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }

/** One stroke as a smooth curve (quadratic segments through the midpoints of consecutive
    samples) instead of a polyline of straight pieces. A stroke with a single point is a dot and
    is drawn by the caller. Points are mapped as (p - origin) * scale + pad. */
private fun strokePath(
    points: List<Offset>,
    originX: Float = 0f,
    originY: Float = 0f,
    scale: Float = 1f,
    padX: Float = 0f,
    padY: Float = 0f,
): android.graphics.Path {
    val path = android.graphics.Path()
    if (points.size < 2) return path
    fun px(i: Int) = (points[i].x - originX) * scale + padX
    fun py(i: Int) = (points[i].y - originY) * scale + padY
    path.moveTo(px(0), py(0))
    for (i in 1 until points.size - 1) {
        path.quadTo(px(i), py(i), (px(i) + px(i + 1)) / 2f, (py(i) + py(i + 1)) / 2f)
    }
    path.lineTo(px(points.size - 1), py(points.size - 1))
    return path
}

/** Re-fits the recorded strokes to a pad whose size changed (the phone was rotated): one uniform
    scale plus centring, so the signature keeps its shape instead of stretching. */
fun SignaturePadState.refitTo(newSize: IntSize) {
    if (newSize.width <= 0 || newSize.height <= 0) return
    val old = canvasSize
    canvasSize = newSize
    if (old.width <= 0 || old.height <= 0 || old == newSize || strokes.isEmpty()) return
    val s = minOf(newSize.width.toFloat() / old.width, newSize.height.toFloat() / old.height)
    val offsetX = (newSize.width - old.width * s) / 2f
    val offsetY = (newSize.height - old.height * s) / 2f
    for (stroke in strokes) {
        for (i in stroke.indices) {
            val p = stroke[i]
            stroke[i] = Offset(p.x * s + offsetX, p.y * s + offsetY)
        }
    }
}

/** Renders the signature to a PNG at [file]: white paper, black ink, cropped to the ink with a
    small margin and scaled into a fixed-size box (see EXPORT_* above). Returns false — writing
    nothing — if nothing was drawn or the file couldn't be written. */
fun SignaturePadState.saveCroppedTo(file: File): Boolean {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var pointCount = 0
    for (stroke in strokes) {
        for (p in stroke) {
            pointCount++
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
    }
    if (pointCount == 0) return false

    val inkWidth = (maxX - minX).coerceAtLeast(1f)
    val inkHeight = (maxY - minY).coerceAtLeast(1f)
    val scale = minOf(
        (EXPORT_MAX_WIDTH - 2 * EXPORT_MARGIN) / inkWidth,
        (EXPORT_MAX_HEIGHT - 2 * EXPORT_MARGIN) / inkHeight,
        EXPORT_MAX_UPSCALE,
    )
    val width = ceil(inkWidth * scale + 2 * EXPORT_MARGIN).toInt()
    val height = ceil(inkHeight * scale + 2 * EXPORT_MARGIN).toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = inkPaint(EXPORT_PEN)
    for (stroke in strokes) {
        when {
            stroke.isEmpty() -> Unit
            stroke.size == 1 -> canvas.drawPoint(
                (stroke[0].x - minX) * scale + EXPORT_MARGIN,
                (stroke[0].y - minY) * scale + EXPORT_MARGIN,
                paint,
            )
            else -> canvas.drawPath(strokePath(stroke, minX, minY, scale, EXPORT_MARGIN, EXPORT_MARGIN), paint)
        }
    }
    val saved = runCatching {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }.getOrDefault(false)
    bitmap.recycle()
    return saved
}

/** Keeps a signature that is half-drawn across a rotation (the Activity is recreated). The
    strokes are flattened to primitive arrays — what a Bundle will accept. */
private val SignaturePadStateSaver = listSaver<SignaturePadState, Any>(
    save = { state ->
        val lengths = IntArray(state.strokes.size) { state.strokes[it].size }
        val flat = FloatArray(lengths.sum() * 2)
        var i = 0
        for (stroke in state.strokes) {
            for (p in stroke) {
                flat[i++] = p.x
                flat[i++] = p.y
            }
        }
        listOf<Any>(state.canvasSize.width, state.canvasSize.height, lengths, flat)
    },
    restore = { saved ->
        SignaturePadState().also { state ->
            state.canvasSize = IntSize(saved[0] as Int, saved[1] as Int)
            val lengths = saved[2] as IntArray
            val flat = saved[3] as FloatArray
            var i = 0
            for (length in lengths) {
                val stroke = mutableStateListOf<Offset>()
                repeat(length) {
                    stroke.add(Offset(flat[i], flat[i + 1]))
                    i += 2
                }
                state.strokes.add(stroke)
            }
        }
    },
)

/**
 * The form-side widget. [signaturePath] is the saved PNG (null = unsigned); [onSignatureChange]
 * gets the new file's path after "تم", or null when the signature is cleared. The caller keeps
 * the path in its own state (the ViewModel), so it survives rotation and is saved with the form.
 */
@Composable
fun SignatureField(
    signaturePath: String?,
    onSignatureChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    dialogTitle: String = "توقيع العميل",
    filePrefix: String = "signature",
    note: String? = null,
) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    // A path that points at a file this device doesn't have (e.g. a synced invoice) is "unsigned".
    val signed = remember(signaturePath) {
        !signaturePath.isNullOrBlank() && File(signaturePath).exists()
    }
    val shape = RoundedCornerShape(12.dp)
    val paperHint = Color(0xFF6B6B6B) // always on white paper, so not theme-dependent

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            if (signed) {
                LocalFileImage(
                    path = signaturePath,
                    contentDescription = dialogTitle,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = paperHint)
                    Text("اضغط هنا ليوقّع العميل", color = paperHint, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Icon(
                Icons.Filled.Fullscreen,
                contentDescription = null,
                tint = paperHint,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(20.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                note.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (signed) {
                TextButton(onClick = { onSignatureChange(null) }) { Text("مسح") }
            }
        }
    }

    if (open) {
        FullScreenSignatureDialog(
            title = dialogTitle,
            onCancel = { open = false },
            onDone = { draft ->
                val file = File(context.filesDir, "signatures/${filePrefix}_${System.currentTimeMillis()}.png")
                if (draft.saveCroppedTo(file)) {
                    onSignatureChange(file.absolutePath)
                    open = false
                } else {
                    Toast.makeText(context, "تعذّر حفظ التوقيع", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun FullScreenSignatureDialog(
    title: String,
    onCancel: () -> Unit,
    onDone: (SignaturePadState) -> Unit,
) {
    // The strokes so far survive a rotation and are re-fitted to the new pad size (refitTo).
    // They are dropped when the dialog closes, so every opening starts on a blank pad.
    val draft = rememberSaveable(saver = SignaturePadStateSaver) { SignaturePadState() }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val background = MaterialTheme.colorScheme.background
        // The dialog is its own window, so the status/navigation icons must be told whether the
        // surface behind them is light or dark — otherwise white icons can vanish on a light pad.
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val bars = WindowCompat.getInsetsController(window, view)
            val lightBackground = background.luminance() > 0.5f
            bars.isAppearanceLightStatusBars = lightBackground
            bars.isAppearanceLightNavigationBars = lightBackground
        }

        Surface(modifier = Modifier.fillMaxSize(), color = background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    // Side margin also keeps the first strokes clear of the system back-gesture edges.
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { draft.clear() }, enabled = !draft.isEmpty) { Text("مسح") }
                    Button(onClick = { onDone(draft) }, enabled = !draft.isEmpty) { Text("تم") }
                }

                val padShape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp)
                        .clip(padShape)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, padShape),
                ) {
                    // The signing line — decoration only, never part of the saved image.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = size.height * 0.72f
                        drawLine(
                            color = Color(0xFFCFCFCF),
                            start = Offset(size.width * 0.06f, y),
                            end = Offset(size.width * 0.94f, y),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                    if (draft.isEmpty) {
                        Text(
                            "وقّع هنا",
                            color = Color(0xFFB5B5B5),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    SignatureCanvas(state = draft, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** The drawing surface: records strokes from the very first touch (a tap leaves a dot) and
    paints them live. Transparent, so the signing line and hint show through from underneath. */
@Composable
private fun SignatureCanvas(state: SignaturePadState, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .onSizeChanged { state.refitTo(it) }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    state.startStroke(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        // Touch samples batched into this event, so fast strokes stay smooth.
                        for (past in change.historical) state.extendLastStroke(past.position)
                        state.extendLastStroke(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        val paint = inkPaint(LIVE_PEN_DP.dp.toPx())
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (points in state.strokes) {
                when {
                    points.isEmpty() -> Unit
                    points.size == 1 -> native.drawPoint(points[0].x, points[0].y, paint)
                    else -> native.drawPath(strokePath(points), paint)
                }
            }
        }
    }
}
