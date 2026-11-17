package com.sonasight.app

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask

/**
 * Receives camera frames, runs the film shader and writes the result to every
 * output CameraX hands us (the viewfinder, and the video encoder while recording).
 * Everything GL happens on one thread.
 */
class FilmProcessor : SurfaceProcessor {

    private val thread = HandlerThread("sonasight-gl").apply { start() }
    private val handler = Handler(thread.looper)
    val executor: Executor = Executor { handler.post(it) }

    @Volatile var look: Look = Look.DEFAULT
    /** Gold stripes on clipped highlights. Only drawn to viewfinder-only outputs, never recorded. */
    @Volatile var zebra: Boolean = false
    @Volatile var maxTextureSize: Int = 4096
        private set

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    private var oes: FilmProgram? = null
    private var still: FilmProgram? = null

    private var input: SurfaceTexture? = null
    private var inputTex = 0
    private var inputAspect = 1f
    private val outputs = LinkedHashMap<SurfaceOutput, EGLSurface>()

    private val texMatrix = FloatArray(16)
    private val outMatrix = FloatArray(16)
    private var frame = 0
    private var released = false

    init {
        handler.post { initGl() }
    }

    private fun initGl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0)
        config = configs[0]
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        pbuffer = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        makeCurrent(pbuffer)
        oes = FilmProgram(external = true)
        still = FilmProgram(external = false)
        val max = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0)
        if (max[0] > 0) maxTextureSize = max[0]
    }

    private fun makeCurrent(s: EGLSurface) {
        EGL14.eglMakeCurrent(display, s, s, context)
    }

    // ---- CameraX side ----

    override fun onInputSurface(request: SurfaceRequest) {
        if (released) {
            request.willNotProvideSurface()
            return
        }
        makeCurrent(pbuffer)
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val res = request.resolution
        val st = SurfaceTexture(tex[0])
        st.setDefaultBufferSize(res.width, res.height)
        val surface = Surface(st)
        st.setOnFrameAvailableListener({ onFrame(it) }, handler)

        input = st
        inputTex = tex[0]
        inputAspect = res.width.toFloat() / res.height

        request.provideSurface(surface, executor) {
            st.setOnFrameAvailableListener(null)
            st.release()
            surface.release()
            makeCurrent(pbuffer)
            GLES20.glDeleteTextures(1, tex, 0)
            if (input === st) input = null
        }
    }

    override fun onOutputSurface(output: SurfaceOutput) {
        if (released) {
            output.close()
            return
        }
        val surface = output.getSurface(executor) {
            val egl = outputs.remove(output)
            if (egl != null) {
                makeCurrent(pbuffer)
                EGL14.eglDestroySurface(display, egl)
            }
            output.close()
        }
        val egl = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (egl == null || egl == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "could not create output surface")
            return
        }
        outputs[output] = egl
    }

    private fun onFrame(st: SurfaceTexture) {
        if (released || st !== input) return
        makeCurrent(pbuffer)
        try {
            st.updateTexImage()
        } catch (e: RuntimeException) {
            return
        }
        st.getTransformMatrix(texMatrix)
        val l = look
        val program = oes ?: return
        frame = (frame + 1) % 1000
        val seed = frame / 1000f
        for ((out, egl) in outputs) {
            makeCurrent(egl)
            out.updateTransformMatrix(outMatrix, texMatrix)
            val size = out.size
            GLES20.glViewport(0, 0, size.width, size.height)
            val scale = minOf(size.width, size.height) / 1080f
            val previewOnly = out.targets == CameraEffect.PREVIEW
            program.draw(inputTex, outMatrix, l, inputAspect, scale.coerceAtLeast(0.6f), seed, zebra && previewOnly)
            EGLExt.eglPresentationTimeANDROID(display, egl, st.timestamp)
            EGL14.eglSwapBuffers(display, egl)
        }
        makeCurrent(pbuffer)
    }

    // ---- Stills ----

    /** Runs the film look over a full-resolution photo. Blocks the caller until done. */
    fun renderStill(src: Bitmap, look: Look): Bitmap {
        val task = FutureTask { renderStillGl(src, look) }
        handler.post(task)
        return task.get()
    }

    private fun renderStillGl(src: Bitmap, look: Look): Bitmap {
        makeCurrent(pbuffer)
        val w = src.width
        val h = src.height
        val tex = IntArray(2)
        GLES20.glGenTextures(2, tex, 0)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        setLinearClamp()
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, src, 0)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[1])
        setLinearClamp()
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )

        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[1], 0,
        )
        GLES20.glViewport(0, 0, w, h)

        val identity = FloatArray(16)
        Matrix.setIdentityM(identity, 0)
        still?.draw(
            tex[0], identity, look, w.toFloat() / h,
            (minOf(w, h) / 1080f).coerceAtLeast(1f), (System.nanoTime() % 1000) / 1000f,
        )

        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDeleteFramebuffers(1, fbo, 0)
        GLES20.glDeleteTextures(2, tex, 0)

        buf.rewind()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.copyPixelsFromBuffer(buf)
        return out
    }

    private fun setLinearClamp() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun release() {
        handler.post {
            released = true
            makeCurrent(pbuffer)
            for ((out, egl) in outputs) {
                EGL14.eglDestroySurface(display, egl)
                out.close()
            }
            outputs.clear()
            oes?.release()
            still?.release()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, pbuffer)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            thread.quitSafely()
        }
    }

    /** Wraps this processor for CameraX. */
    fun effect(targets: Int): CameraEffect = FilmEffect(targets, executor, this)

    private class FilmEffect(targets: Int, executor: Executor, processor: SurfaceProcessor) :
        CameraEffect(targets, executor, processor, Consumer<Throwable> { Log.e(TAG, "effect error", it) })

    companion object {
        private const val TAG = "SonaSight"
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
