package com.sonasight.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Small pieces that give SonaSight its woodblock-print feel, kept to black, greys and gold:
 * title cartouches, bokashi fades, a faint seigaiha wave pattern and a gold seal.
 * Section labels follow akshatthakur.com: "// label" in monospace, with Japanese beside it.
 */

/** "// film  フィルム" */
@Composable
fun SectionLabel(en: String, jp: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicText("// $en", style = TextStyle(color = Ash, fontSize = 11.sp, fontFamily = FontFamily.Monospace))
        if (jp != null) {
            Spacer(Modifier.width(8.dp))
            BasicText(jp, style = TextStyle(color = Gold.copy(alpha = 0.8f), fontSize = 10.sp))
        }
    }
}

/** An English label with its Japanese beside it, smaller and quieter. */
@Composable
fun Bilingual(en: String, jp: String, color: Color = Mist, size: Float = 10f) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(en, style = Label.copy(color = color, fontSize = size.sp))
        Spacer(Modifier.width(5.dp))
        BasicText(jp, style = TextStyle(color = color.copy(alpha = color.alpha * 0.6f), fontSize = (size - 1f).sp))
    }
}

/**
 * The title box printed on ukiyo-e: a double gold rule around a name.
 * With [vertical] the text is set top to bottom, one character per line.
 */
@Composable
fun Cartouche(
    modifier: Modifier = Modifier,
    filled: Color = Ink.copy(alpha = 0.6f),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .background(filled)
            .border(1.dp, Gold.copy(alpha = 0.85f), RectangleShape)
            .padding(2.dp)
            .border(0.5.dp, Gold.copy(alpha = 0.45f), RectangleShape)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun VerticalText(text: String, color: Color = Bone, size: Float = 12f) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        text.forEach { ch ->
            BasicText(ch.toString(), style = TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight.Medium))
        }
    }
}

/** Bokashi: the soft printed gradient ukiyo-e uses for skies. Gold fading into black. */
@Composable
fun Bokashi(height: Dp = 120.dp, strength: Float = 0.16f, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(height)
            .background(Brush.verticalGradient(listOf(Gold.copy(alpha = strength), Color.Transparent))),
    )
}

/** Seigaiha: rows of overlapping concentric arcs, drawn very faintly. */
@Composable
fun Seigaiha(modifier: Modifier = Modifier, alpha: Float = 0.07f, radius: Dp = 22.dp) {
    Canvas(modifier.fillMaxSize().graphicsLayer(alpha = alpha)) {
        val r = radius.toPx()
        val stroke = Stroke(1.dp.toPx())
        val rowStep = r / 2f
        var row = 0
        var y = -r
        while (y < size.height + r) {
            val offset = if (row % 2 == 0) 0f else r
            var x = -r + offset
            while (x < size.width + r) {
                val c = Offset(x, y)
                // fill hides the arcs of the row above, which is what makes the scales overlap
                drawCircle(Ink, r, c)
                for (k in 0 until 4) drawCircle(Gold, r * (1f - k * 0.24f), c, style = stroke)
                x += r * 2f
            }
            y += rowStep
            row++
        }
    }
}

/** 写 ("to capture") in a square gold seal. */
@Composable
fun Hanko(size: Dp = 34.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size)
            .background(Gold.copy(alpha = 0.9f))
            .padding(2.dp)
            .border(1.dp, Ink.copy(alpha = 0.55f), RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "写",
            style = TextStyle(color = Ink, fontSize = (size.value * 0.52f).sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
        )
    }
}

/** The seal pressing down and lifting away, once per [key]. */
@Composable
fun HankoStamp(key: Int, modifier: Modifier = Modifier) {
    if (key == 0) return
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(140))
        kotlinx.coroutines.delay(650)
        anim.animateTo(2f, tween(380))
    }
    val v = anim.value
    val scale = if (v < 1f) 1.6f - 0.6f * v else 1f
    val a = if (v < 1f) v else (2f - v).coerceIn(0f, 1f)
    Box(modifier.scale(scale).graphicsLayer(alpha = a)) { Hanko() }
}
