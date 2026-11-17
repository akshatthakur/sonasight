package com.sonasight.app

/**
 * Film stocks are original looks. Each one is a set of numbers the film shader reads:
 * tone curve, colour response, grain, halation and vignette.
 */
data class FilmStock(
    val id: String,
    val name: String,
    val iso: String,
    val note: String,
    val contrast: Float = 0.25f,      // S-curve amount, can be negative for flatter tones
    val saturation: Float = 1f,
    val fade: Float = 0f,             // lifted blacks
    val shadowTint: FloatArray = floatArrayOf(0f, 0f, 0f),
    val highlightTint: FloatArray = floatArrayOf(0f, 0f, 0f),
    val warmth: Float = 0f,           // -1 cool .. +1 warm
    val tint: Float = 0f,             // -1 green .. +1 magenta
    val mixer: FloatArray = IDENTITY, // row-major 3x3 channel mixer
    val greenShift: Float = 0f,       // hue rotation for greens (+ toward teal, - toward olive)
    val warmSat: Float = 0f,          // extra saturation for reds/oranges/skin
    val blueSat: Float = 0f,          // extra saturation for blues
    val grain: Float = 0.3f,
    val grainSize: Float = 1.5f,
    val halation: Float = 0f,
    val vignette: Float = 0.2f,
    val mono: Boolean = false,
    val monoFilter: FloatArray = floatArrayOf(0.2126f, 0.7152f, 0.0722f),
) {
    companion object {
        val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}

enum class Overlay(val label: String) { NONE("OFF"), PHI("PHI"), SPIRAL("SPIRAL"), TRIANGLES("TRI") }

enum class Aspect(val label: String, val long: Int, val short: Int) {
    R3_2("3:2", 3, 2), R4_3("4:3", 4, 3), R1_1("1:1", 1, 1), R16_9("16:9", 16, 9), XPAN("65:24", 65, 24);

    val ratio: Float get() = long.toFloat() / short
}

data class Scene(
    val id: String,
    val name: String,
    val stockId: String,
    val lens: Int = 35,
    val aspect: Aspect = Aspect.R3_2,
    val overlay: Overlay = Overlay.PHI,
    val ev: Float = 0f,
    val warmth: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val grainMul: Float = 1f,
    val halation: Float = 0f,
    val vignette: Float = 0f,
)

object Films {
    val stocks = listOf(
        FilmStock(
            "meridian", "MERIDIAN", "100", "Clean daylight slide. Honest colour.",
            contrast = 0.28f, saturation = 1.05f,
            shadowTint = floatArrayOf(0f, 0f, 0.01f), highlightTint = floatArrayOf(0.01f, 0.006f, 0f),
            warmth = 0.04f, warmSat = 0.04f, blueSat = 0.06f,
            grain = 0.22f, grainSize = 1.3f, halation = 0.04f, vignette = 0.14f,
        ),
        FilmStock(
            "kiln", "KILN", "50", "Dense, saturated slide. Deep greens and skies.",
            contrast = 0.55f, saturation = 1.32f,
            shadowTint = floatArrayOf(-0.005f, 0f, 0.02f),
            mixer = floatArrayOf(1.04f, -0.03f, -0.01f, -0.02f, 1.04f, -0.02f, -0.01f, -0.03f, 1.04f),
            greenShift = 0.01f, warmSat = 0.14f, blueSat = 0.2f,
            grain = 0.14f, grainSize = 1.2f, vignette = 0.24f,
        ),
        FilmStock(
            "ledger", "LEDGER", "200", "Muted documentary colour, hard shadows.",
            contrast = 0.46f, saturation = 0.76f, fade = 0.02f,
            shadowTint = floatArrayOf(0f, 0.01f, 0.025f), highlightTint = floatArrayOf(0.02f, 0.012f, 0f),
            greenShift = -0.02f, warmSat = -0.08f, blueSat = -0.25f,
            grain = 0.42f, grainSize = 1.6f, halation = 0.05f, vignette = 0.3f,
        ),
        FilmStock(
            "arcade", "ARCADE", "400", "Everyday negative. Teal shadows, crossed colour.",
            contrast = 0.4f, saturation = 0.9f, fade = 0.04f,
            shadowTint = floatArrayOf(-0.01f, 0.01f, 0.03f), highlightTint = floatArrayOf(0.03f, 0.01f, -0.02f),
            mixer = floatArrayOf(1.02f, -0.02f, 0f, 0.02f, 0.96f, 0.02f, 0f, 0.04f, 0.96f),
            greenShift = 0.03f, warmSat = 0.05f, blueSat = -0.1f,
            grain = 0.5f, grainSize = 1.7f, halation = 0.1f, vignette = 0.24f,
        ),
        FilmStock(
            "reel", "REEL", "250", "Flat cinema stock. Room in every shadow.",
            contrast = -0.1f, saturation = 0.72f, fade = 0.06f,
            shadowTint = floatArrayOf(0f, 0.01f, 0.02f), highlightTint = floatArrayOf(0.015f, 0.01f, 0f),
            greenShift = 0.02f, grain = 0.34f, grainSize = 1.5f, vignette = 0.2f,
        ),
        FilmStock(
            "amber", "AMBER", "160", "Warm and nostalgic. Honey highlights.",
            contrast = 0.2f, saturation = 0.95f, fade = 0.05f,
            shadowTint = floatArrayOf(0.02f, 0.008f, -0.01f), highlightTint = floatArrayOf(0.04f, 0.02f, -0.02f),
            warmth = 0.35f, warmSat = 0.18f, blueSat = -0.15f,
            grain = 0.4f, grainSize = 1.6f, halation = 0.15f, vignette = 0.3f,
        ),
        FilmStock(
            "pale", "PALE", "160", "Soft portrait negative. Gentle on skin.",
            contrast = 0.1f, saturation = 0.88f, fade = 0.03f,
            shadowTint = floatArrayOf(0.005f, 0f, 0.01f), highlightTint = floatArrayOf(0.015f, 0.008f, 0.005f),
            warmth = 0.08f, warmSat = -0.05f,
            grain = 0.24f, grainSize = 1.3f, halation = 0.08f, vignette = 0.18f,
        ),
        FilmStock(
            "halo", "HALO", "800T", "Tungsten night stock. Red glow around lights.",
            contrast = 0.34f, saturation = 1.05f, fade = 0.03f,
            shadowTint = floatArrayOf(0f, 0.01f, 0.03f), highlightTint = floatArrayOf(0.03f, 0f, -0.01f),
            warmth = -0.42f, tint = 0.05f, greenShift = 0.03f,
            grain = 0.6f, grainSize = 1.8f, halation = 0.85f, vignette = 0.3f,
        ),
        FilmStock(
            "silver", "SILVER SKIP", "200", "Bleach bypass. Metallic, low colour, hard.",
            contrast = 0.72f, saturation = 0.45f,
            shadowTint = floatArrayOf(0f, 0f, 0.01f),
            grain = 0.4f, grainSize = 1.5f, vignette = 0.34f,
        ),
        FilmStock(
            "ink", "INK", "100", "Fine black and white. Yellow filter.",
            contrast = 0.5f, mono = true, monoFilter = floatArrayOf(0.36f, 0.54f, 0.10f),
            grain = 0.42f, grainSize = 1.3f, vignette = 0.3f,
        ),
        FilmStock(
            "soot", "SOOT", "1600", "Pushed black and white. Red filter, heavy grain.",
            contrast = 0.82f, fade = 0.05f, mono = true, monoFilter = floatArrayOf(0.6f, 0.35f, 0.05f),
            grain = 0.85f, grainSize = 2.0f, vignette = 0.45f,
        ),
    )

    val scenes = listOf(
        Scene("streets", "STREETS", "ledger", lens = 28, overlay = Overlay.PHI, grainMul = 1.1f),
        Scene("temples", "TEMPLES", "amber", lens = 35, overlay = Overlay.SPIRAL, contrast = 0.05f, vignette = 0.1f),
        Scene("nightlife", "NIGHTLIFE", "halo", lens = 35, overlay = Overlay.PHI, ev = -0.3f, grainMul = 1.2f),
        Scene("lanterns", "LANTERNS", "halo", lens = 50, overlay = Overlay.SPIRAL, ev = -0.7f, warmth = 0.38f, halation = 0.3f),
        Scene("buildings", "BUILDINGS", "reel", lens = 24, overlay = Overlay.TRIANGLES, contrast = 0.22f, aspect = Aspect.R4_3),
        Scene("people", "PEOPLE", "pale", lens = 50, overlay = Overlay.SPIRAL, vignette = 0.05f),
        Scene("markets", "MARKETS", "arcade", lens = 35, overlay = Overlay.PHI),
        Scene("rain", "RAIN", "reel", lens = 35, overlay = Overlay.PHI, warmth = -0.15f, halation = 0.2f),
        Scene("golden", "GOLDEN HOUR", "amber", lens = 35, overlay = Overlay.SPIRAL, warmth = 0.1f, halation = 0.2f),
        Scene("nature", "NATURE", "kiln", lens = 24, overlay = Overlay.PHI, aspect = Aspect.R3_2),
        Scene("food", "FOOD", "kiln", lens = 50, overlay = Overlay.PHI, aspect = Aspect.R1_1, saturation = -0.1f),
        Scene("noir", "NOIR", "soot", lens = 35, overlay = Overlay.TRIANGLES),
        Scene("vista", "VISTA", "meridian", lens = 28, overlay = Overlay.PHI, aspect = Aspect.XPAN),
    )

    fun stock(id: String) = stocks.firstOrNull { it.id == id } ?: stocks.first()
    fun scene(id: String) = scenes.firstOrNull { it.id == id } ?: scenes.first()
}

/** Flattened parameters the GL shader reads every frame. */
class Look(
    val contrast: Float,
    val saturation: Float,
    val fade: Float,
    val shadowTint: FloatArray,
    val highlightTint: FloatArray,
    val warmth: Float,
    val tint: Float,
    val mixerColumnMajor: FloatArray,
    val hue: FloatArray,
    val grain: Float,
    val grainSize: Float,
    val halation: Float,
    val vignette: Float,
    val mono: Boolean,
    val monoFilter: FloatArray,
) {
    companion object {
        fun of(stock: FilmStock, scene: Scene?, grainOn: Boolean = true): Look {
            val s = scene
            val m = stock.mixer
            // GL wants column-major; our mixer is written row-major
            val cm = floatArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])
            return Look(
                contrast = stock.contrast + (s?.contrast ?: 0f),
                saturation = (stock.saturation + (s?.saturation ?: 0f)).coerceAtLeast(0f),
                fade = stock.fade,
                shadowTint = stock.shadowTint,
                highlightTint = stock.highlightTint,
                warmth = stock.warmth + (s?.warmth ?: 0f),
                tint = stock.tint,
                mixerColumnMajor = cm,
                hue = floatArrayOf(stock.greenShift, stock.warmSat, stock.blueSat),
                grain = if (grainOn) stock.grain * (s?.grainMul ?: 1f) else 0f,
                grainSize = stock.grainSize,
                halation = (stock.halation + (s?.halation ?: 0f)).coerceIn(0f, 1.5f),
                vignette = (stock.vignette + (s?.vignette ?: 0f)).coerceIn(0f, 1f),
                mono = stock.mono,
                monoFilter = stock.monoFilter,
            )
        }

        val DEFAULT = of(Films.stocks.first(), null)
    }
}
