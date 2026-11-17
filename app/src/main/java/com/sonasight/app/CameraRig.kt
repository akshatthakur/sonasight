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
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
import java.util.concurrent.atomic.AtomicInteger
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
        camera?.cameraInfo?.zoomState?.removeObservers(owner)
        camera = try {
            p.bindToLifecycle(owner, selector, group.build())
        } catch (e: Exception) {
            Log.e(TAG, "bind failed", e)
            null
        }
        camera?.cameraInfo?.zoomState?.observe(owner) { z ->
            onZoom?.invoke(z.zoomRatio, z.minZoomRatio, z.maxZoomRatio)
        }
    }

    /** Called with (ratio, min, max) whenever the zoom changes. */
    var onZoom: ((Float, Float, Float) -> Unit)? = null

    val hasTorch: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    fun setTorch(on: Boolean) {
        camera?.cameraControl?.enableTorch(on && hasTorch)
    }

    fun setZoomRatio(ratio: Float) {
        val cam = camera ?: return
        val z = cam.cameraInfo.zoomState.value ?: return
        cam.cameraControl.setZoomRatio(ratio.coerceIn(z.minZoomRatio, z.maxZoomRatio))
    }

    fun zoomRatio(): Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    fun unbind() {
        recording?.stop()
        camera?.cameraInfo?.zoomState?.removeObservers(owner)
        provider?.unbindAll()
        camera = null
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

    /** Tap to focus and meter. With snap focus on, a tap only sets the exposure. */
    fun focus(previewView: PreviewView, x: Float, y: Float, exposureOnly: Boolean = false) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val flags = if (exposureOnly) FocusMeteringAction.FLAG_AE else FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        val action = FocusMeteringAction.Builder(point, flags)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    // ---- Photo ----

    private val developer = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)

    /** Photos still being developed. */
    val developingCount: Int get() = pending.get()

    /**
     * Takes a photo, develops it through the film look and saves it.
     * With [toRoll] the frame goes into the private roll folder instead of the gallery.
     * [onCaptured] fires as soon as the frame is in hand, so a burst can go on to the next.
     * [done] gets the saved uri (null for roll frames or failures) and a small thumbnail.
     */
    fun takePhoto(
        look: Look,
        aspect: Aspect,
        front: Boolean,
        dateStamp: Boolean,
        info: ShotInfo,
        toRoll: Boolean,
        quiet: Boolean = false,
        onCaptured: (Boolean) -> Unit = {},
        done: (ok: Boolean, uri: Uri?, thumb: Bitmap?) -> Unit,
    ) {
        if (!quiet) sound.play(MediaActionSound.SHUTTER_CLICK)
        capture(front, aspect, Int.MAX_VALUE) { bmp ->
            if (bmp == null) {
                main.post { onCaptured(false); done(false, null, null) }
                return@capture
            }
            // don't let frames pile up faster than they can be developed
            while (pending.get() >= 2) Thread.sleep(40)
            main.post { onCaptured(true) }
            developAndSave(bmp, look, dateStamp, info, toRoll, done)
        }
    }

    /**
     * Night mode: takes [frames] pictures in a row, lines them up and averages them, then
     * develops the result like any other photo. [onFrame] reports each frame as it lands.
     */
    fun takeNight(
        frames: Int,
        look: Look,
        aspect: Aspect,
        front: Boolean,
        dateStamp: Boolean,
        info: ShotInfo,
        toRoll: Boolean,
        onFrame: (Int) -> Unit,
        done: (ok: Boolean, uri: Uri?, thumb: Bitmap?) -> Unit,
    ) {
        sound.play(MediaActionSound.SHUTTER_CLICK)
        val stack = NightStack()
        fun next(i: Int) {
            capture(front, aspect, NIGHT_MAX_SIDE) { bmp ->
                if (bmp != null) {
                    try { stack.add(bmp) } catch (e: Throwable) { Log.e(TAG, "stack failed", e) }
                    bmp.recycle()
                }
                main.post { onFrame(i + 1) }
                if (i + 1 < frames) {
                    main.post { next(i + 1) }
                } else if (stack.frames == 0) {
                    main.post { done(false, null, null) }
                } else {
                    val result = try { stack.result() } catch (e: Throwable) { null }
                    if (result == null) main.post { done(false, null, null) }
                    else developAndSave(result, look, dateStamp, info, toRoll, done)
                }
            }
        }
        next(0)
    }

    /** Grabs one frame as an upright, cropped bitmap no larger than [maxSide]. */
    private fun capture(front: Boolean, aspect: Aspect, maxSide: Int, got: (Bitmap?) -> Unit) {
        val ic = imageCapture ?: return got(null)
        ic.takePicture(work, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bmp = try {
                    upright(image, front, aspect, maxSide)
                } catch (e: Throwable) {
                    Log.e(TAG, "capture failed", e)
                    null
                } finally {
                    image.close()
                }
                got(bmp)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "capture failed", exception)
                got(null)
            }
        })
    }

    private fun upright(image: ImageProxy, front: Boolean, aspect: Aspect, maxSide: Int): Bitmap {
        val raw = image.toBitmap()
        val m = Matrix()
        m.postRotate(image.imageInfo.rotationDegrees.toFloat())
        if (front) m.postScale(-1f, 1f)
        var bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (bmp !== raw) raw.recycle()
        bmp = Imaging.cropToAspect(bmp, aspect.ratio, null, 0f)
        return Imaging.fitForGl(bmp, minOf(maxSide, processor.maxTextureSize))
    }

    private fun developAndSave(
        bmp: Bitmap,
        look: Look,
        dateStamp: Boolean,
        info: ShotInfo,
        toRoll: Boolean,
        done: (ok: Boolean, uri: Uri?, thumb: Bitmap?) -> Unit,
    ) {
        pending.incrementAndGet()
        developer.execute {
            var ok = false
            var uri: Uri? = null
            var thumb: Bitmap? = null
            try {
                val developed = processor.renderStill(bmp, look)
                bmp.recycle()
                if (dateStamp) Media.drawDate(developed, info.taken)
                val file = if (toRoll) Media.writeJpeg(context, developed, info, Media.rollDir(context))
                else Media.writeJpeg(context, developed, info)
                if (!toRoll) uri = Media.publishImage(context, file)
                val th = 240
                thumb = Bitmap.createScaledBitmap(
                    developed, (developed.width * th / developed.height.toFloat()).toInt().coerceAtLeast(1), th, true,
                )
                developed.recycle()
                ok = toRoll || uri != null
            } catch (e: Throwable) {
                Log.e(TAG, "develop failed", e)
            } finally {
                pending.decrementAndGet()
            }
            main.post { done(ok, uri, thumb) }
        }
    }

    // ---- Snap focus ----

    /** True when the lens can be set to a distance by hand. */
    @OptIn(ExperimentalCamera2Interop::class)
    fun canSnapFocus(): Boolean {
        val cam = camera ?: return false
        val min = runCatching {
            Camera2CameraInfo.from(cam.cameraInfo).getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        }.getOrNull() ?: 0f
        return min > 0f
    }

    /** Fixes focus at [meters] (0 for infinity), or goes back to autofocus when null. */
    @OptIn(ExperimentalCamera2Interop::class)
    fun setSnapFocus(meters: Float?) {
        val cam = camera ?: return
        val c2 = Camera2CameraControl.from(cam.cameraControl)
        if (meters == null || !canSnapFocus()) {
            c2.clearCaptureRequestOptions()
            return
        }
        val diopters = if (meters <= 0f) 0f else 1f / meters
        c2.setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
                .build(),
        )
    }

    // ---- Video ----

    val isRecording: Boolean get() = recording != null

    fun startRecording(location: android.location.Location?, onTick: (Long) -> Unit, onSaved: (Uri?) -> Unit) {
        val vc = videoCapture ?: return
        val name = "SONA_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SonaSight")
        }
        val options = MediaStoreOutputOptions.Builder(
            context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).apply { if (location != null) setLocation(location) }.build()

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
        developer.shutdown()
        sound.release()
        processor.release()
    }

    companion object {
        private const val TAG = "SonaSight"
        /** Night frames are stacked at this size so the stack fits in memory. */
        private const val NIGHT_MAX_SIDE = 3000
    }
}
