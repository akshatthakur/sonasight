package com.sonasight.app

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Averages several handheld frames into one cleaner night picture.
 *
 * Each new frame is lined up with the first by finding the shift that matches best, coarse to
 * fine on small greyscale copies. Pixels that differ a lot from the first frame (people walking,
 * cars) keep the first frame's value instead, so moving things don't turn into ghosts.
 * Noise drops by about the square root of the number of frames.
 */
class NightStack {
    private var w = 0
    private var h = 0
    private var ref: IntArray? = null
    private var buf: IntArray? = null
    private var sum: ShortArray? = null
    private var count = 0
    private val refPyramid = HashMap<Int, Gray>()

    private class Gray(val w: Int, val h: Int, val v: FloatArray)

    val frames: Int get() = count

    fun add(b: Bitmap) {
        if (ref == null) {
            w = b.width; h = b.height
            val r = IntArray(w * h)
            b.getPixels(r, 0, w, 0, 0, w, h)
            ref = r
            buf = IntArray(w * h)
            val s = ShortArray(w * h * 3)
            for (i in r.indices) {
                val p = r[i]
                s[i * 3] = ((p shr 16) and 0xFF).toShort()
                s[i * 3 + 1] = ((p shr 8) and 0xFF).toShort()
                s[i * 3 + 2] = (p and 0xFF).toShort()
            }
            sum = s
            for (scale in SCALES) refPyramid[scale] = gray(b, scale)
            count = 1
            return
        }
        if (b.width != w || b.height != h) return
        val r = ref!!
        val px = buf!!
        val s = sum!!
        b.getPixels(px, 0, w, 0, 0, w, h)
        val (dx, dy) = align(b)

        for (y in 0 until h) {
            val sy = y + dy
            val rowOk = sy in 0 until h
            for (x in 0 until w) {
                val i = y * w + x
                val rp = r[i]
                val sx = x + dx
                var p = if (rowOk && sx in 0 until w) px[sy * w + sx] else rp
                val rr = (rp shr 16) and 0xFF
                val rg = (rp shr 8) and 0xFF
                val rb = rp and 0xFF
                var pr = (p shr 16) and 0xFF
                var pg = (p shr 8) and 0xFF
                var pb = p and 0xFF
                if (abs(pr - rr) + abs(pg - rg) + abs(pb - rb) > GHOST) {
                    pr = rr; pg = rg; pb = rb
                }
                s[i * 3] = (s[i * 3] + pr).toShort()
                s[i * 3 + 1] = (s[i * 3 + 1] + pg).toShort()
                s[i * 3 + 2] = (s[i * 3 + 2] + pb).toShort()
            }
        }
        count++
    }

    fun result(): Bitmap {
        val s = sum ?: throw IllegalStateException("no frames")
        val out = buf ?: IntArray(w * h)
        val n = count.coerceAtLeast(1)
        for (i in 0 until w * h) {
            val r = s[i * 3] / n
            val g = s[i * 3 + 1] / n
            val b = s[i * 3 + 2] / n
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        ref = null; buf = null; sum = null; refPyramid.clear()
        return bmp
    }

    /** Best (dx, dy) in full-size pixels so that frame(x + dx, y + dy) matches ref(x, y). */
    private fun align(b: Bitmap): Pair<Int, Int> {
        var bx = 0
        var by = 0
        var first = true
        for (scale in SCALES) {
            val a = refPyramid[scale] ?: continue
            val g = gray(b, scale)
            val range = if (first) 12 else 2
            val cx = if (first) 0 else bx * (prevScale(scale) / scale)
            val cy = if (first) 0 else by * (prevScale(scale) / scale)
            var best = Float.MAX_VALUE
            var nx = cx
            var ny = cy
            for (oy in cy - range..cy + range) for (ox in cx - range..cx + range) {
                val d = sad(a, g, ox, oy)
                if (d < best) { best = d; nx = ox; ny = oy }
            }
            bx = nx; by = ny
            first = false
        }
        val last = SCALES.last()
        return (bx * last) to (by * last)
    }

    private fun prevScale(scale: Int): Int {
        val i = SCALES.indexOf(scale)
        return if (i > 0) SCALES[i - 1] else scale
    }

    /** Mean absolute difference over the middle of the frame. */
    private fun sad(a: Gray, g: Gray, dx: Int, dy: Int): Float {
        val x0 = a.w / 5
        val x1 = a.w - a.w / 5
        val y0 = a.h / 5
        val y1 = a.h - a.h / 5
        var total = 0f
        var n = 0
        var y = y0
        while (y < y1) {
            val sy = y + dy
            if (sy in 0 until g.h) {
                var x = x0
                while (x < x1) {
                    val sx = x + dx
                    if (sx in 0 until g.w) {
                        total += abs(a.v[y * a.w + x] - g.v[sy * g.w + sx])
                        n++
                    }
                    x += 1
                }
            }
            y += 1
        }
        return if (n == 0) Float.MAX_VALUE else total / n
    }

    private fun gray(b: Bitmap, scale: Int): Gray {
        val sw = (b.width / scale).coerceAtLeast(1)
        val sh = (b.height / scale).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(b, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (small !== b) small.recycle()
        val v = FloatArray(sw * sh)
        for (i in px.indices) {
            val p = px[i]
            v[i] = 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
        }
        return Gray(sw, sh, v)
    }

    companion object {
        /** Coarse to fine: 1/8, 1/4, then 1/2 size. */
        private val SCALES = intArrayOf(8, 4, 2)
        private const val GHOST = 90
    }
}
