package com.sonasight.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaActionSound
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class Mode { PHOTO, VIDEO }

class CameraRig(
    private val context: Context,
    private val owner: LifecycleOwner,
    val processor: FilmProcessor,
) {
    private val main = Handler(Looper.getMainLooper())
    private val work = Executors.newSingleThreadExecutor()
    private val sound = MediaActionSound().apply {
        load(MediaActionSound.SHUTTER_CLICK)
        load(MediaActionSound.START_VIDEO_RECORDING)
        load(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var rotation = android.view.Surface.ROTATION_0

    var hasFront = false
        private set

    fun start(previewView: PreviewView, mode: Mode, front: Boolean, onReady: () -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = future.get()
            hasFront = provider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true
            bind(previewView, mode, front)
            onReady()
        }, ContextCompat.getMainExecutor(context))
    }

    fun bind(previewView: PreviewView, mode: Mode, front: Boolean) {
        val p = provider ?: return
        p.unbindAll()
        imageCapture = null
        videoCapture = null

        val ratio = if (mode == Mode.PHOTO) AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val group = UseCaseGroup.Builder().addUseCase(preview)
        if (mode == Mode.PHOTO) {
            val ic = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(4032, 3024),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                            ),
                        )
                        .build(),
                )
                .setTargetRotation(rotation)
                .build()
            imageCapture = ic
            group.addUseCase(ic)
            group.addEffect(processor.effect(CameraEffect.PREVIEW))
        } else {
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)),
                )
                .build()
            val vc = VideoCapture.withOutput(recorder)
            vc.targetRotation = rotation
            videoCapture = vc
            group.addUseCase(vc)
            group.addEffect(processor.effect(CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE))
        }

        val selector = if (front && hasFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        camera = try {
            p.bindToLifecycle(owner, selector, group.build())
        } catch (e: Exception) {
            Log.e(TAG, "bind failed", e)
            null
        }
    }

    fun setRotation(r: Int) {
        rotation = r
        imageCapture?.targetRotation = r
        if (recording == null) videoCapture?.targetRotation = r
    }

    /** Lens choice as a 35mm-equivalent focal length. The main camera is treated as ~24mm. */
    fun setFocal(mm: Int, retries: Int = 5) {
        val cam = camera ?: return
        val zs = cam.cameraInfo.zoomState.value
        if (zs == null) {
            // zoom range arrives a moment after binding
            if (retries > 0) main.postDelayed({ setFocal(mm, retries - 1) }, 200)
            return
        }
        cam.cameraControl.setZoomRatio((mm / 24f).coerceIn(zs.minZoomRatio, zs.maxZoomRatio))
    }

    fun setEv(ev: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return
        val step = state.exposureCompensationStep.toFloat()
        if (step <= 0f) return
        val idx = (ev / step).roundToInt().coerceIn(state.exposureCompensationRange.lower, state.exposureCompensationRange.upper)
        cam.cameraControl.setExposureCompensationIndex(idx)
    }

    fun focus(previewView: PreviewView, x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    // ---- Photo ----

    fun takePhoto(look: Look, aspect: Aspect, front: Boolean, done: (Uri?, Bitmap?) -> Unit) {
        val ic = imageCapture ?: return done(null, null)
        sound.play(MediaActionSound.SHUTTER_CLICK)
        ic.takePicture(work, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val result = try {
                    develop(image, look, aspect, front)
                } catch (e: Throwable) {
                    Log.e(TAG, "develop failed", e)
                    null
                } finally {
                    image.close()
                }
                main.post { done(result?.first, result?.second) }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "capture failed", exception)
                main.post { done(null, null) }
            }
        })
    }

    private fun develop(image: ImageProxy, look: Look, aspect: Aspect, front: Boolean): Pair<Uri?, Bitmap> {
        val raw = image.toBitmap()
        val m = Matrix()
        m.postRotate(image.imageInfo.rotationDegrees.toFloat())
        if (front) m.postScale(-1f, 1f)
        var bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (bmp !== raw) raw.recycle()

        bmp = crop(bmp, aspect)

        val max = processor.maxTextureSize
        if (bmp.width > max || bmp.height > max) {
            val s = max.toFloat() / maxOf(bmp.width, bmp.height)
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
            bmp.recycle()
            bmp = scaled
        }
        if (bmp.config != Bitmap.Config.ARGB_8888) {
            val c = bmp.copy(Bitmap.Config.ARGB_8888, false)
            bmp.recycle()
            bmp = c
        }

        val developed = processor.renderStill(bmp, look)
        bmp.recycle()

        val uri = saveJpeg(developed)
        val thumbH = 240
        val thumb = Bitmap.createScaledBitmap(
            developed, (developed.width * thumbH / developed.height.toFloat()).toInt().coerceAtLeast(1), thumbH, true,
        )
        developed.recycle()
        return uri to thumb
    }

    /** Crops to the chosen ratio, keeping the long side of the ratio on the long side of the image. */
    private fun crop(src: Bitmap, aspect: Aspect): Bitmap {
        val landscape = src.width >= src.height
        val target = aspect.ratio
        val w = src.width
        val h = src.height
        val cur = if (landscape) w.toFloat() / h else h.toFloat() / w
        if (kotlin.math.abs(cur - target) < 0.01f) return src
        var cw = w
        var ch = h
        if (landscape) {
            if (cur > target) cw = (h * target).roundToInt() else ch = (w / target).roundToInt()
        } else {
            if (cur > target) ch = (w * target).roundToInt() else cw = (h / target).roundToInt()
        }
        val out = Bitmap.createBitmap(src, (w - cw) / 2, (h - ch) / 2, cw, ch)
        if (out !== src) src.recycle()
        return out
    }

    private fun saveJpeg(bmp: Bitmap): Uri? {
        val name = "SONA_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SonaSight")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    // ---- Video ----

    val isRecording: Boolean get() = recording != null

    fun startRecording(onTick: (Long) -> Unit, onSaved: (Uri?) -> Unit) {
        val vc = videoCapture ?: return
        val name = "SONA_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SonaSight")
        }
        val options = MediaStoreOutputOptions.Builder(
            context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()

        var pending = vc.output.prepareRecording(context, options)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        sound.play(MediaActionSound.START_VIDEO_RECORDING)
        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Status -> onTick(event.recordingStats.recordedDurationNanos / 1_000_000L)
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    onSaved(if (event.hasError()) null else event.outputResults.outputUri)
                }
                else -> Unit
            }
        }
    }

    fun stopRecording() {
        recording?.stop()
        sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    fun shutdown() {
        recording?.stop()
        provider?.unbindAll()
        work.shutdown()
        sound.release()
        processor.release()
    }

    companion object {
        private const val TAG = "SonaSight"
    }
}
