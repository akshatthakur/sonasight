package com.sonasight.app

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.Calendar

/**
 * Lays a set of photos out as one contact sheet: black paper, frames in rows, gold frame
 * numbers and each frame's film underneath, like a sheet printed from a developed roll.
 */
object ContactSheet {

    private const val INK = 0xFF0A0A0A.toInt()
    private const val GOLD = 0xFFC9A55A.toInt()
    private const val MIST = 0xFF8A8A8A.toInt()
    private const val BONE = 0xFFF2F2F0.toInt()

    /** SonaSight photos taken today, oldest first, leaving out earlier contact sheets. */
    fun today(context: Context): List<Uri> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000
        val uris = ArrayList<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} >= ? " +
            "AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ?"
        val args = arrayOf("Pictures/SonaSight%", start.toString(), "SONA_SHEET%")
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args,
            "${MediaStore.Images.Media.DATE_ADDED} ASC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext() && uris.size < MAX) {
                uris += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(id))
            }
        }
        return uris
    }

    /** Builds the sheet and saves it to Pictures/SonaSight. */
    fun make(context: Context, uris: List<Uri>, title: String): Uri? {
        val frames = uris.take(MAX)
        if (frames.isEmpty()) return null
        val cols = if (frames.size > 24) 6 else 4
        val cell = if (cols == 6) 420 else 560
        val gap = 40
        val margin = 110
        val caption = 74
        val header = 230
        val rows = (frames.size + cols - 1) / cols
        val width = margin * 2 + cols * cell + (cols - 1) * gap
        val height = margin + header + rows * (cell + caption) + (rows - 1) * gap + margin

        val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(INK)

        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // header: wordmark, title, and the site-style comment label
        p.typeface = bold; p.color = GOLD; p.textSize = 46f; p.letterSpacing = 0.35f
        c.drawText("SONASIGHT", margin.toFloat(), (margin + 50).toFloat(), p)
        p.typeface = mono; p.letterSpacing = 0.05f; p.color = BONE; p.textSize = 40f
        c.drawText(title, margin.toFloat(), (margin + 120).toFloat(), p)
        p.color = MIST; p.textSize = 30f
        c.drawText("// contact sheet  密着  ·  ${frames.size} frames", margin.toFloat(), (margin + 170).toFloat(), p)
        p.color = GOLD; p.strokeWidth = 2f
        c.drawLine(margin.toFloat(), (margin + 200).toFloat(), (width - margin).toFloat(), (margin + 200).toFloat(), p)

        frames.forEachIndexed { i, uri ->
            val col = i % cols
            val row = i / cols
            val x = margin + col * (cell + gap)
            val y = margin + header + row * (cell + caption + gap)
            val thumb = runCatching { context.contentResolver.loadThumbnail(uri, Size(cell, cell), null) }.getOrNull()
            if (thumb != null) {
                val s = minOf(cell.toFloat() / thumb.width, cell.toFloat() / thumb.height)
                val tw = thumb.width * s
                val th = thumb.height * s
                val dst = RectF(x + (cell - tw) / 2f, y + (cell - th) / 2f, x + (cell + tw) / 2f, y + (cell + th) / 2f)
                c.drawBitmap(thumb, Rect(0, 0, thumb.width, thumb.height), dst, Paint(Paint.FILTER_BITMAP_FLAG))
                thumb.recycle()
            }
            p.typeface = mono; p.letterSpacing = 0f
            p.color = GOLD; p.textSize = 30f
            c.drawText((i + 1).toString().padStart(2, '0'), x.toFloat(), (y + cell + 42).toFloat(), p)
            val film = runCatching {
                context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION) }
            }.getOrNull()
            if (!film.isNullOrBlank()) {
                p.color = MIST; p.textSize = 24f
                val text = ellipsize(film, p, cell - 70f)
                c.drawText(text, x + 66f, (y + cell + 42).toFloat(), p)
            }
        }

        val file = File(context.cacheDir, Media.stamp("SONA_SHEET") + ".jpg")
        file.outputStream().use { sheet.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        sheet.recycle()
        return Media.publishImage(context, file)
    }

    private fun ellipsize(text: String, p: Paint, max: Float): String {
        if (p.measureText(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    private const val MAX = 48
}
