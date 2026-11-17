package com.sonasight.app

import android.content.Context
import android.opengl.Matrix
import androidx.annotation.OptIn
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** Holds the look a video effect reads every frame, so sliders update the preview live. */
class LookRef(@Volatile var look: Look)

/** The film shader as a media3 effect, for previewing and exporting edited videos. */
@OptIn(UnstableApi::class)
class FilmGlEffect(private val ref: LookRef) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = FilmShaderProgram(ref, useHdr)
}

@OptIn(UnstableApi::class)
private class FilmShaderProgram(private val ref: LookRef, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private var program: FilmProgram? = null
    private var width = 1
    private var height = 1
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val p = program ?: FilmProgram(external = false).also { program = it }
        val seed = ((presentationTimeUs / 33_333L) % 1000L) / 1000f
        p.draw(
            inputTexId, identity, ref.look, width.toFloat() / height,
            (minOf(width, height) / 1080f).coerceAtLeast(0.6f), seed,
        )
    }

    override fun release() {
        super.release()
        program?.release()
        program = null
    }
}
