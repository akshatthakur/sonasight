package com.sonasight.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.net.Uri
import kotlin.math.roundToInt

object Imaging {

    /**
     * The largest rectangle of the given ratio inside a w x h image, in 0..1 coordinates.
     * [ratio] is long side / short side, null keeps the whole image.
     * [portrait] null follows the image's own orientation.
     * [pan] -1..1 slides the crop along whichever axis has room.
     */
    fun cropRect(w: Int, h: Int, ratio: Float?, portrait: Boolean?, pan: Float): RectF {
        if (ratio == null) return RectF(0f, 0f, 1f, 1f)
        val landscape = portrait?.not() ?: (w >= h)
        val target = if (landscape) ratio else 1f / ratio
        val cur = w.toFloat() / h
        return if (cur > target) {
            val fw = target / cur
            val left = (1f - fw) / 2f * (1f + pan.coerceIn(-1f, 1f))
            RectF(left, 0f, left + fw, 1f)
        } else {
            val fh = cur / target
            val top = (1f - fh) / 2f * (1f + pan.coerceIn(-1f, 1f))
            RectF(0f, top, 1f, top + fh)
        }
    }

    fun cropToAspect(src: Bitmap, ratio: Float?, portrait: Boolean?, pan: Float): Bitmap {
        val r = cropRect(src.width, src.height, ratio, portrait, pan)
        val x = (r.left * src.width).roundToInt()
        val y = (r.top * src.height).roundToInt()
        val cw = (r.width() * src.width).roundToInt().coerceIn(1, src.width - x)
        val ch = (r.height() * src.height).roundToInt().coerceIn(1, src.height - y)
        if (x == 0 && y == 0 && cw == src.width && ch == src.height) return src
        val out = Bitmap.createBitmap(src, x, y, cw, ch)
        if (out !== src) src.recycle()
        return out
    }

    /** Scales down to fit the GPU's texture limit and makes sure the pixels are ARGB_8888. */
    fun fitForGl(src: Bitmap, max: Int): Bitmap {
        var bmp = src
        if (bmp.width > max || bmp.height > max) {
            val s = max.toFloat() / maxOf(bmp.width, bmp.height)
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        if (bmp.config != Bitmap.Config.ARGB_8888) {
            val c = bmp.copy(Bitmap.Config.ARGB_8888, false)
            bmp.recycle()
            bmp = c
        }
        return bmp
    }

    /** Decodes an image upright, no larger than [maxSide] on its long side. */
    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            val w = info.size.width
            val h = info.size.height
            val long = maxOf(w, h)
            if (long > maxSide) {
                val s = maxSide.toFloat() / long
                decoder.setTargetSize((w * s).roundToInt().coerceAtLeast(1), (h * s).roundToInt().coerceAtLeast(1))
            }
        }
    }
}
