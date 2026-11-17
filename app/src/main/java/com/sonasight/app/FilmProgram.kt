package com.sonasight.app

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * The film shader. One source, compiled twice: once sampling the live camera
 * (external OES texture) and once sampling a still photo (regular 2D texture).
 */
class FilmProgram(private val external: Boolean) {

    private val program: Int
    private val aPos: Int
    private val aUv: Int
    private val uTexMatrix: Int
    private val uTex: Int
    private val uContrast: Int
    private val uSat: Int
    private val uFade: Int
    private val uShadowTint: Int
    private val uHighTint: Int
    private val uWb: Int
    private val uMix: Int
    private val uHue: Int
    private val uGrain: Int
    private val uHalo: Int
    private val uVignette: Int
    private val uMono: Int
    private val uSeed: Int
    private val uTexAspect: Int

    private val quad: FloatBuffer = floatBuffer(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val uvs: FloatBuffer = floatBuffer(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

    init {
        val fs = (if (external) HEADER_OES else HEADER_2D) + FRAGMENT_BODY
        program = link(compile(GLES20.GL_VERTEX_SHADER, VERTEX), compile(GLES20.GL_FRAGMENT_SHADER, fs))
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aUv = GLES20.glGetAttribLocation(program, "aUv")
        uTexMatrix = loc("uTexMatrix")
        uTex = loc("uTex")
        uContrast = loc("uContrast")
        uSat = loc("uSat")
        uFade = loc("uFade")
        uShadowTint = loc("uShadowTint")
        uHighTint = loc("uHighTint")
        uWb = loc("uWb")
        uMix = loc("uMix")
        uHue = loc("uHue")
        uGrain = loc("uGrain")
        uHalo = loc("uHalo")
        uVignette = loc("uVignette")
        uMono = loc("uMono")
        uSeed = loc("uSeed")
        uTexAspect = loc("uTexAspect")
    }

    /**
     * @param grainScale output pixels per "reference" pixel (1080 short side), keeps grain
     *                   the same visual size on preview and full-res stills.
     */
    fun draw(
        tex: Int,
        texMatrix: FloatArray,
        look: Look,
        texAspect: Float,
        grainScale: Float,
        seed: Float,
    ) {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)

        GLES20.glUniform1f(uContrast, look.contrast)
        GLES20.glUniform1f(uSat, look.saturation)
        GLES20.glUniform1f(uFade, look.fade)
        GLES20.glUniform3fv(uShadowTint, 1, look.shadowTint, 0)
        GLES20.glUniform3fv(uHighTint, 1, look.highlightTint, 0)
        GLES20.glUniform2f(uWb, look.warmth, look.tint)
        GLES20.glUniformMatrix3fv(uMix, 1, false, look.mixerColumnMajor, 0)
        GLES20.glUniform3fv(uHue, 1, look.hue, 0)
        GLES20.glUniform3f(uGrain, look.grain, look.grainSize, grainScale)
        GLES20.glUniform2f(uHalo, look.halation, 0.0045f)
        GLES20.glUniform1f(uVignette, look.vignette)
        GLES20.glUniform4f(
            uMono, if (look.mono) 1f else 0f,
            look.monoFilter[0], look.monoFilter[1], look.monoFilter[2],
        )
        GLES20.glUniform1f(uSeed, seed)
        GLES20.glUniform1f(uTexAspect, texAspect)

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 0, uvs)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUv)
    }

    fun release() = GLES20.glDeleteProgram(program)

    private fun loc(name: String) = GLES20.glGetUniformLocation(program, name)

    companion object {
        private const val VERTEX = """
attribute vec4 aPos;
attribute vec4 aUv;
uniform mat4 uTexMatrix;
varying vec2 vUv;
varying vec2 vPos;
void main() {
    gl_Position = aPos;
    vUv = (uTexMatrix * aUv).xy;
    vPos = aUv.xy;
}
"""

        private const val HEADER_OES = """#extension GL_OES_EGL_image_external : require
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform samplerExternalOES uTex;
"""

        private const val HEADER_2D = """#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTex;
"""

        private const val FRAGMENT_BODY = """
varying vec2 vUv;
varying vec2 vPos;
uniform float uContrast;
uniform float uSat;
uniform float uFade;
uniform vec3 uShadowTint;
uniform vec3 uHighTint;
uniform vec2 uWb;
uniform mat3 uMix;
uniform vec3 uHue;
uniform vec3 uGrain;     // amount, size, scale
uniform vec2 uHalo;      // amount, radius (uv)
uniform float uVignette;
uniform vec4 uMono;      // on, filter rgb
uniform float uSeed;
uniform float uTexAspect;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float bright(vec3 s) { return max(luma(s) - 0.70, 0.0); }

void main() {
    vec3 c = texture2D(uTex, vUv).rgb;

    // halation: light bleeding back through the film base, tinted red-orange
    if (uHalo.x > 0.001) {
        vec3 acc = vec3(0.0);
        vec2 r = vec2(uHalo.y, uHalo.y * uTexAspect);
        for (int i = 0; i < 10; i++) {
            float a = float(i) * 0.6283185;
            vec2 o = vec2(cos(a), sin(a)) * r;
            vec3 s1 = texture2D(uTex, vUv + o).rgb;
            vec3 s2 = texture2D(uTex, vUv + o * 2.4).rgb;
            acc += s1 * bright(s1) + s2 * bright(s2) * 0.6;
        }
        acc /= 16.0;
        c += acc * vec3(1.0, 0.30, 0.12) * uHalo.x * 3.2;
    }

    // white balance
    c *= vec3(1.0 + uWb.x * 0.09 + uWb.y * 0.03, 1.0 - uWb.y * 0.06, 1.0 - uWb.x * 0.11 + uWb.y * 0.03);

    // channel mixer
    c = clamp(uMix * c, 0.0, 1.0);

    // selective colour
    vec3 hsv = rgb2hsv(c);
    float h = hsv.x;
    float gw = smoothstep(0.14, 0.22, h) * (1.0 - smoothstep(0.42, 0.52, h));
    hsv.x = fract(hsv.x + gw * uHue.x);
    float ow = 1.0 - smoothstep(0.0, 0.09, min(abs(h - 0.06), abs(h - 1.06)));
    float bw = 1.0 - smoothstep(0.0, 0.09, abs(h - 0.6));
    hsv.y = clamp(hsv.y * (1.0 + ow * uHue.y + bw * uHue.z), 0.0, 1.0);
    c = hsv2rgb(hsv);

    // saturation
    float l = luma(c);
    c = mix(vec3(l), c, uSat);

    // black and white with a coloured filter
    if (uMono.x > 0.5) {
        c = vec3(dot(c, uMono.yzw));
    }

    // tone: filmic S-curve and a soft shoulder
    c = clamp(c, 0.0, 1.0);
    vec3 s = c * c * (3.0 - 2.0 * c);
    c = clamp(mix(c, s, uContrast), 0.0, 1.0);
    c = 1.0 - pow(1.0 - c, vec3(1.08));

    // split toning
    l = luma(c);
    c += uShadowTint * (1.0 - smoothstep(0.0, 0.55, l)) + uHighTint * smoothstep(0.45, 1.0, l);
    if (uMono.x > 0.5) c = mix(vec3(luma(c)), c, 0.35);

    // faded blacks
    c = uFade + c * (1.0 - uFade);

    // vignette
    vec2 d = vPos - 0.5;
    c *= mix(1.0, smoothstep(0.95, 0.25, length(d)), uVignette);

    // grain, strongest in the midtones
    vec2 gp = floor(gl_FragCoord.xy / max(uGrain.y * uGrain.z, 1.0));
    float n = hash(gp + uSeed * 97.0) + hash(gp * 1.7 + uSeed * 31.0) - 1.0;
    float gy = luma(c);
    c += n * uGrain.x * (0.25 + 3.0 * gy * (1.0 - gy)) * 0.16;

    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""

        private fun floatBuffer(vararg v: Float): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(v); position(0)
            }

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(s)
                GLES20.glDeleteShader(s)
                throw RuntimeException("shader compile failed: $log")
            }
            return s
        }

        private fun link(vs: Int, fs: Int): Int {
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, vs)
            GLES20.glAttachShader(p, fs)
            GLES20.glLinkProgram(p)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) throw RuntimeException("program link failed: " + GLES20.glGetProgramInfoLog(p))
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            return p
        }
    }
}
