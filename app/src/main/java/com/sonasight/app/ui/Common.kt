package com.sonasight.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

// Palette: black, greys, white, and sona gold.
val Ink = Color(0xFF000000)
val Coal = Color(0xFF0E0E0E)
val Slate = Color(0xFF1C1C1C)
val Ash = Color(0xFF6E6E6E)
val Mist = Color(0xFFB4B4B4)
val Bone = Color(0xFFF2F2F0)
val Gold = Color(0xFFC9A55A)

val Label = TextStyle(
    color = Mist, fontSize = 10.sp, letterSpacing = 2.2.sp,
    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
)
val Numeral = TextStyle(color = Bone, fontSize = 12.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
val Body = TextStyle(color = Ash, fontSize = 12.sp, fontFamily = FontFamily.SansSerif)

@Composable
fun TextKey(
    text: String,
    active: Boolean = false,
    numeric: Boolean = false,
    color: Color? = null,
    onClick: () -> Unit,
) {
    val base = if (numeric) Numeral.copy(fontSize = 12.sp) else Label
    Box(
        Modifier.clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = base.copy(color = color ?: if (active) Gold else Mist))
    }
}

/**
 * A thin slider: label, hairline track with a gold mark, and the value.
 * Double-tap resets to [default].
 */
@Composable
fun FineSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    val view = LocalView.current
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = Label, modifier = Modifier.width(92.dp))
        Canvas(
            Modifier.weight(1f).height(32.dp)
                .pointerInput(range) {
                    fun at(x: Float): Float {
                        val f = (x / size.width).coerceIn(0f, 1f)
                        return range.start + f * (range.endInclusive - range.start)
                    }
                    detectTapGestures(
                        onDoubleTap = { change(default); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
                        onTap = { change(at(it.x)) },
                    )
                }
                .pointerInput(range) {
                    detectHorizontalDragGestures { ch, _ ->
                        ch.consume()
                        val f = (ch.position.x / size.width).coerceIn(0f, 1f)
                        var v = range.start + f * (range.endInclusive - range.start)
                        // a little magnet at the default value
                        if (abs(v - default) < (range.endInclusive - range.start) * 0.02f) v = default
                        if (v != current) {
                            if (v == default) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            change(v)
                        }
                    }
                },
        ) {
            val y = size.height / 2
            val span = range.endInclusive - range.start
            val fx = ((value - range.start) / span).coerceIn(0f, 1f) * size.width
            val dx = ((default - range.start) / span).coerceIn(0f, 1f) * size.width
            drawLine(Ash, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            drawLine(Mist, Offset(dx, y - 4.dp.toPx()), Offset(dx, y + 4.dp.toPx()), 1.dp.toPx())
            drawLine(Gold, Offset(minOf(dx, fx), y), Offset(maxOf(dx, fx), y), 1.5.dp.toPx())
            drawCircle(Gold, 5.dp.toPx(), Offset(fx, y))
        }
        Spacer(Modifier.width(12.dp))
        BasicText(format(value), style = Numeral.copy(color = Mist, fontSize = 11.sp), modifier = Modifier.width(44.dp))
    }
}

fun pct(v: Float) = "${(v * 100).roundToInt()}"
fun signed(v: Float, digits: Int = 1): String {
    if (abs(v) < 0.005f) return "0"
    return (if (v > 0) "+" else "−") + "%.${digits}f".format(abs(v))
}
