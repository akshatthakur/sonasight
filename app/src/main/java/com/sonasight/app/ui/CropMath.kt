package com.sonasight.app.ui

import androidx.compose.ui.geometry.Offset
import com.sonasight.app.Aspect
import kotlin.math.abs

/** A crop in 0..1 coordinates of the picture, y pointing down. */
data class NRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w: Float get() = r - l
    val h: Float get() = b - t
    val isFull: Boolean get() = l <= 0.001f && t <= 0.001f && r >= 0.999f && b >= 0.999f

    companion object {
        val FULL = NRect(0f, 0f, 1f, 1f)
    }
}

/** How the crop's shape is held: not at all, the picture's own shape, or a fixed ratio. */
sealed class Ratio {
    object Free : Ratio()
    object Original : Ratio()
    data class Fixed(val aspect: Aspect) : Ratio()
}

/** What a touch grabbed: a corner (0 top-left, clockwise), an edge (0 left, clockwise), or the middle. */
sealed class Grab {
    data class Corner(val i: Int) : Grab()
    data class Edge(val i: Int) : Grab()
    object Inside : Grab()
}

object CropMath {
    /** Smallest crop side, as a share of the picture. */
    const val MIN = 0.08f

    /**
     * The width/height in pixels the crop has to keep, or null when it's free.
     * A fixed ratio follows the picture's orientation unless [turned].
     */
    fun target(ratio: Ratio, w: Int, h: Int, turned: Boolean): Float? = when (ratio) {
        Ratio.Free -> null
        Ratio.Original -> w.toFloat() / h
        is Ratio.Fixed -> {
            val landscape = (w >= h) != turned
            if (landscape) ratio.aspect.ratio else 1f / ratio.aspect.ratio
        }
    }

    /** k such that crop height (in 0..1) = crop width (in 0..1) * k keeps the pixel ratio. */
    private fun k(w: Int, h: Int, target: Float) = w.toFloat() / (h * target)

    /** The biggest centred crop with the given shape. */
    fun fit(w: Int, h: Int, target: Float?): NRect {
        if (target == null) return NRect.FULL
        val kk = k(w, h, target)
        var cw = 1f
        var ch = cw * kk
        if (ch > 1f) { ch = 1f; cw = ch / kk }
        return NRect((1f - cw) / 2f, (1f - ch) / 2f, (1f + cw) / 2f, (1f + ch) / 2f)
    }

    /** Slides the crop, stopping at the picture's edges. */
    fun move(c: NRect, dx: Float, dy: Float): NRect {
        val x = dx.coerceIn(-c.l, 1f - c.r)
        val y = dy.coerceIn(-c.t, 1f - c.b)
        return NRect(c.l + x, c.t + y, c.r + x, c.b + y)
    }

    /** Pinch: [factor] over 1 tightens the crop around its centre, under 1 widens it. */
    fun zoom(c: NRect, factor: Float, w: Int, h: Int, target: Float?): NRect {
        if (factor <= 0f) return c
        var cw = c.w / factor
        var ch = c.h / factor
        if (target != null) ch = cw * k(w, h, target)
        // never past the picture, never smaller than MIN
        val over = maxOf(cw, ch)
        if (over > 1f) { cw /= over; ch /= over }
        val under = minOf(cw, ch)
        if (under < MIN) { cw *= MIN / under; ch *= MIN / under }
        val cx = (c.l + c.r) / 2f
        val cy = (c.t + c.b) / 2f
        val l = (cx - cw / 2f).coerceIn(0f, 1f - cw)
        val t = (cy - ch / 2f).coerceIn(0f, 1f - ch)
        return NRect(l, t, l + cw, t + ch)
    }

    /** Drags corner [i] to [p], with the opposite corner staying put. */
    fun corner(c: NRect, i: Int, p: Offset, w: Int, h: Int, target: Float?): NRect {
        // opposite corner, and which way this corner points from it
        val ox = if (i == 0 || i == 3) c.r else c.l
        val oy = if (i == 0 || i == 1) c.b else c.t
        val sx = if (i == 0 || i == 3) -1f else 1f
        val sy = if (i == 0 || i == 1) -1f else 1f
        val availW = if (sx > 0) 1f - ox else ox
        val availH = if (sy > 0) 1f - oy else oy
        var cw = ((p.x - ox) * sx).coerceIn(MIN, availW)
        var ch = ((p.y - oy) * sy).coerceIn(MIN, availH)
        if (target != null) {
            val kk = k(w, h, target)
            // follow whichever direction the finger moved further, then keep it inside
            if (ch > cw * kk) cw = ch / kk else ch = cw * kk
            if (cw > availW) { cw = availW; ch = cw * kk }
            if (ch > availH) { ch = availH; cw = ch / kk }
            if (minOf(cw, ch) < MIN) {
                val s = MIN / minOf(cw, ch)
                if (cw * s <= availW && ch * s <= availH) { cw *= s; ch *= s }
            }
        }
        val l = if (sx > 0) ox else ox - cw
        val t = if (sy > 0) oy else oy - ch
        return NRect(l, t, l + cw, t + ch)
    }

    /** Drags edge [i] to [p]. Only used when the shape is free. */
    fun edge(c: NRect, i: Int, p: Offset): NRect = when (i) {
        0 -> c.copy(l = p.x.coerceIn(0f, c.r - MIN))
        1 -> c.copy(t = p.y.coerceIn(0f, c.b - MIN))
        2 -> c.copy(r = p.x.coerceIn(c.l + MIN, 1f))
        else -> c.copy(b = p.y.coerceIn(c.t + MIN, 1f))
    }

    /** What a touch at [pos] (pixels, in a view of [vw] x [vh]) grabs. Corners win over edges. */
    fun grab(c: NRect, pos: Offset, vw: Float, vh: Float, slop: Float, free: Boolean): Grab {
        val l = c.l * vw; val t = c.t * vh; val r = c.r * vw; val b = c.b * vh
        val corners = listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b))
        corners.forEachIndexed { i, o ->
            if (abs(pos.x - o.x) < slop && abs(pos.y - o.y) < slop) return Grab.Corner(i)
        }
        if (free) {
            val inY = pos.y in t..b
            val inX = pos.x in l..r
            if (inY && abs(pos.x - l) < slop) return Grab.Edge(0)
            if (inX && abs(pos.y - t) < slop) return Grab.Edge(1)
            if (inY && abs(pos.x - r) < slop) return Grab.Edge(2)
            if (inX && abs(pos.y - b) < slop) return Grab.Edge(3)
        }
        return Grab.Inside
    }
}
