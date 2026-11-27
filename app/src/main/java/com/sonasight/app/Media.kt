package com.sonasight.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What gets written into a photo's metadata. */
data class ShotInfo(
    val film: String,
    val scene: String?,
    val focalMm: Int?,
    val location: Location?,
    val taken: Date = Date(),
    /** Degrees to rotate the picture to level it, from the level at the moment of capture. */
    val straighten: Float? = null,
)

object Media {

    fun stamp(prefix: String = "SONA") =
        prefix + "_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    /** The gold date in the corner, like old film cameras: '26 11 17 */
    fun drawDate(bmp: Bitmap, date: Date) {
        val text = SimpleDateFormat("''yy MM dd", Locale.US).format(date)
        val c = Canvas(bmp)
        val side = minOf(bmp.width, bmp.height)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = side * 0.038f
            color = 0xFFE6B45C.toInt()
            letterSpacing = 0.08f
            setShadowLayer(side * 0.006f, 0f, 0f, 0xCCE09A3A.toInt())
        }
        val margin = side * 0.045f
        val w = p.measureText(text)
        c.drawText(text, bmp.width - margin - w, bmp.height - margin, p)
    }

    /** Writes a JPEG to a temp file with SonaSight metadata, ready to publish or keep in a roll. */
    fun writeJpeg(context: Context, bmp: Bitmap, info: ShotInfo, dir: File = context.cacheDir, base: ExifInterface? = null): File {
        dir.mkdirs()
        val f = File(dir, stamp() + ".jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        val exif = ExifInterface(f.absolutePath)
        // carry over the original's time and place when editing
        if (base != null) {
            for (tag in listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
            )) base.getAttribute(tag)?.let { exif.setAttribute(tag, it) }
        } else {
            val t = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(info.taken)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, t)
            exif.setAttribute(ExifInterface.TAG_DATETIME, t)
            exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
            info.location?.let { exif.setGpsInfo(it) }
            info.focalMm?.let { exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, it.toString()) }
        }
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "SonaSight")
        val desc = listOfNotNull(info.film, info.scene).joinToString(" / ")
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, desc)
        val level = info.straighten?.let { "; straighten: " + String.format(Locale.US, "%.2f", it) } ?: ""
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "Film: $desc$level")
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()
        return f
    }

    /** Copies a finished JPEG into Pictures/SonaSight and deletes the temp file. */
    fun publishImage(context: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SonaSight")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val r = context.contentResolver
        val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        r.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        r.update(uri, values, null, null)
        file.delete()
        return uri
    }

    /** Copies a finished video into Movies/SonaSight and deletes the temp file. */
    fun publishVideo(context: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SonaSight")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val r = context.contentResolver
        val uri = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        r.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        r.update(uri, values, null, null)
        file.delete()
        return uri
    }

    // ---- roll mode: frames wait in private storage until the roll is developed ----

    fun rollDir(context: Context) = File(context.filesDir, "roll")

    fun rollCount(context: Context) = rollDir(context).listFiles { f -> f.name.endsWith(".jpg") }?.size ?: 0

    /** Moves every frame on the roll into the gallery, in the order they were shot. */
    fun developRoll(context: Context): List<Uri> {
        val files = rollDir(context).listFiles { f -> f.name.endsWith(".jpg") }?.sortedBy { it.name } ?: return emptyList()
        return files.mapNotNull { publishImage(context, it) }
    }

    /** The straighten angle SonaSight saved in a photo, if any. */
    fun savedStraighten(exif: ExifInterface?): Float? {
        val c = exif?.getAttribute(ExifInterface.TAG_USER_COMMENT) ?: return null
        return Regex("straighten: (-?[0-9.]+)").find(c)?.groupValues?.get(1)?.toFloatOrNull()
    }
}
