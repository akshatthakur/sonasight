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

    fun focus(previewView: PreviewView, x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    // ---- Photo ----

    /**
     * Takes a photo, develops it through the film look and saves it.
     * With [toRoll] the frame goes into the private roll folder instead of the gallery.
     * [done] gets the saved uri (null for roll frames or failures) and a small thumbnail.
     */
    fun takePhoto(
        look: Look,
        aspect: Aspect,
        front: Boolean,
        dateStamp: Boolean,
        info: ShotInfo,
        toRoll: Boolean,
        done: (ok: Boolean, uri: Uri?, thumb: Bitmap?) -> Unit,
    ) {
        val ic = imageCapture ?: return done(false, null, null)
        sound.play(MediaActionSound.SHUTTER_CLICK)
        ic.takePicture(work, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                var ok = false
                var uri: Uri? = null
                var thumb: Bitmap? = null
                try {
                    val developed = develop(image, look, aspect, front)
                    image.close()
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
                    runCatching { image.close() }
                }
                main.post { done(ok, uri, thumb) }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "capture failed", exception)
                main.post { done(false, null, null) }
            }
        })
    }

    private fun develop(image: ImageProxy, look: Look, aspect: Aspect, front: Boolean): Bitmap {
        val raw = image.toBitmap()
        val m = Matrix()
        m.postRotate(image.imageInfo.rotationDegrees.toFloat())
        if (front) m.postScale(-1f, 1f)
        var bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (bmp !== raw) raw.recycle()
        bmp = Imaging.cropToAspect(bmp, aspect.ratio, null, 0f)
        bmp = Imaging.fitForGl(bmp, processor.maxTextureSize)
        val developed = processor.renderStill(bmp, look)
        bmp.recycle()
        return developed
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
        sound.release()
        processor.release()
    }

    companion object {
        private const val TAG = "SonaSight"
    }
}
