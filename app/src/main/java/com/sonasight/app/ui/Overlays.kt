package com.sonasight.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

const val PHI = 1.618034f
private const val INV = 0.618034f

/** Gold phi grid: lines at 0.382 and 0.618 of each side. */
fun DrawScope.phiGrid(r: Rect, color: Color, width: Float) {
    for (f in floatArrayOf(1f - INV, INV)) {
        val x = r.left + r.width * f
        val y = r.top + r.height * f
        drawLine(color, Offset(x, r.top), Offset(x, r.bottom), width)
        drawLine(color, Offset(r.left, y), Offset(r.right, y), width)
    }
    // small marks where the lines cross: the four power points
    for (fx in floatArrayOf(1f - INV, INV)) for (fy in floatArrayOf(1f - INV, INV)) {
        drawCircle(color, width * 2.2f, Offset(r.left + r.width * fx, r.top + r.height * fy))
    }
}

/**
 * Golden spiral built from quarter arcs inside successively smaller golden rectangles.
 * Drawn in a unit golden rectangle, then stretched to the frame. [turn] 0..3 picks
 * which corner the spiral winds into.
 */
fun DrawScope.goldenSpiral(r: Rect, turn: Int, color: Color, width: Float) {
    val portrait = r.height > r.width
    var x = 0f; var y = 0f; var w = PHI; var h = 1f
    val squares = ArrayList<Rect>()
    val pts = ArrayList<Offset>()
    for (i in 0 until 11) {
        val cx: Float; val cy: Float; val rad: Float; val a0: Float
        when (i % 4) {
            0 -> { rad = h; squares += Rect(x, y, x + h, y + h); cx = x + h; cy = y + h; a0 = 180f; x += h; w -= h }
            1 -> { rad = w; squares += Rect(x, y, x + w, y + w); cx = x; cy = y + w; a0 = 270f; y += w; h -= w }
            2 -> { rad = h; squares += Rect(x + w - h, y, x + w, y + h); cx = x + w - h; cy = y; a0 = 0f; w -= h }
            else -> { rad = w; squares += Rect(x, y + h - w, x + w, y + h); cx = x + w; cy = y + h - w; a0 = 90f; h -= w }
        }
        for (k in 0..12) {
            val a = Math.toRadians((a0 + 90f * k / 12f).toDouble())
            pts += Offset(cx + rad * cos(a).toFloat(), cy + rad * sin(a).toFloat())
        }
    }
    val flipX = turn == 1 || turn == 2
    val flipY = turn == 2 || turn == 3
    fun map(p: Offset): Offset {
        // unit space: x in [0, PHI], y in [0, 1]
        var u = p.x / PHI
        var v = p.y
        if (flipX) u = 1f - u
        if (flipY) v = 1f - v
        if (portrait) { val t = u; u = v; v = t }
        return Offset(r.left + u * r.width, r.top + v * r.height)
    }
    val path = Path()
    pts.forEachIndexed { i, p -> val m = map(p); if (i == 0) path.moveTo(m.x, m.y) else path.lineTo(m.x, m.y) }
    drawPath(path, color, style = Stroke(width * 1.4f, cap = StrokeCap.Round))
    val faint = color.copy(alpha = color.alpha * 0.45f)
    for (s in squares.take(6)) {
        val a = map(Offset(s.left, s.top)); val b = map(Offset(s.right, s.bottom))
        drawRect(
            faint, Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
            androidx.compose.ui.geometry.Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)),
            style = Stroke(width * 0.7f),
        )
    }
}

/** Golden triangles: one diagonal plus perpendiculars from the other two corners. */
fun DrawScope.goldenTriangles(r: Rect, turn: Int, color: Color, width: Float) {
    val flip = turn % 2 == 1
    val a = if (flip) Offset(r.left, r.top) else Offset(r.left, r.bottom)
    val b = if (flip) Offset(r.right, r.bottom) else Offset(r.right, r.top)
    val c1 = if (flip) Offset(r.right, r.top) else Offset(r.left, r.top)
    val c2 = if (flip) Offset(r.left, r.bottom) else Offset(r.right, r.bottom)
    drawLine(color, a, b, width, cap = StrokeCap.Round)
    for (c in listOf(c1, c2)) {
        val f = foot(c, a, b)
        drawLine(color, c, f, width, cap = StrokeCap.Round)
        drawCircle(color, width * 2.2f, f)
    }
}

private fun foot(p: Offset, a: Offset, b: Offset): Offset {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    val t = ((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2
    return Offset(a.x + ab.x * t, a.y + ab.y * t)
}

/** Fits a w:h ratio inside [outer], centred. */
fun fitRect(outer: Rect, ratio: Float): Rect {
    val outerRatio = outer.width / outer.height
    return if (outerRatio > ratio) {
        val w = outer.height * ratio
        Rect(outer.center.x - w / 2, outer.top, outer.center.x + w / 2, outer.bottom)
    } else {
        val h = outer.width / ratio
        Rect(outer.left, outer.center.y - h / 2, outer.right, outer.center.y + h / 2)
    }
}

