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
    val category: String = "FILM",
) {
    companion object {
        val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}

enum class Overlay(val label: String) { NONE("OFF"), PHI("PHI"), SPIRAL("SPIRAL"), TRIANGLES("TRI") }

enum class Aspect(val label: String, val long: Int, val short: Int) {
    R3_2("3:2", 3, 2), R4_3("4:3", 4, 3), R5_4("5:4", 5, 4), R1_1("1:1", 1, 1), R16_9("16:9", 16, 9), XPAN("65:24", 65, 24);

    val ratio: Float get() = long.toFloat() / short

    /** Written the way the phone is held: 4:5 and 9:16 upright, 5:4 and 16:9 sideways. */
    fun label(upright: Boolean) = if (long == short) "1:1" else if (upright) "$short:$long" else "$long:$short"
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

    /**
     * MEISHO: looks named after places in Japan, each tuned to what that place is known for.
     * The badge is the place name in kanji instead of an ISO number.
     */
    val meisho = listOf(
        FilmStock(
            "shibuya", "SHIBUYA", "渋谷", "Night crossings and glass towers. Teal shadows, warm signs, crisp edges.",
            contrast = 0.42f, saturation = 1.0f, fade = 0.02f,
            shadowTint = floatArrayOf(-0.01f, 0.015f, 0.035f), highlightTint = floatArrayOf(0.03f, 0.01f, -0.01f),
            warmth = -0.3f, greenShift = 0.04f, warmSat = 0.12f,
            grain = 0.45f, grainSize = 1.6f, halation = 0.55f, vignette = 0.25f, category = "MEISHO",
        ),
        FilmStock(
            "shinjuku", "SHINJUKU", "新宿", "Neon in the rain. Electric blues against hot reds.",
            contrast = 0.38f, saturation = 1.15f, fade = 0.03f,
            shadowTint = floatArrayOf(0f, 0f, 0.045f), highlightTint = floatArrayOf(0.035f, 0f, 0.02f),
            warmth = -0.45f, tint = 0.12f,
            mixer = floatArrayOf(1.05f, -0.03f, -0.02f, -0.02f, 1.0f, 0.02f, 0f, -0.02f, 1.02f),
            warmSat = 0.15f, blueSat = 0.2f,
            grain = 0.55f, grainSize = 1.7f, halation = 0.7f, vignette = 0.32f, category = "MEISHO",
        ),
        FilmStock(
            "asakusa", "ASAKUSA", "浅草", "Temple gates and paper lanterns. Vermilion, with cyan-green shadows.",
            contrast = 0.4f, saturation = 0.95f, fade = 0.03f,
            shadowTint = floatArrayOf(-0.01f, 0.015f, 0.025f), highlightTint = floatArrayOf(0.04f, 0.015f, 0f),
            warmth = 0.2f, warmSat = 0.2f, blueSat = -0.15f,
            grain = 0.45f, grainSize = 1.6f, halation = 0.3f, vignette = 0.3f, category = "MEISHO",
        ),
        FilmStock(
            "shimokita", "SHIMOKITA", "下北", "Everyday streets. Soft, faded and a little nostalgic.",
            contrast = 0.18f, saturation = 0.85f, fade = 0.07f,
            shadowTint = floatArrayOf(-0.01f, 0.02f, 0.03f), highlightTint = floatArrayOf(0.025f, 0.01f, 0f),
            warmth = 0.05f, greenShift = 0.03f,
            grain = 0.5f, grainSize = 1.7f, halation = 0.08f, vignette = 0.2f, category = "MEISHO",
        ),
        FilmStock(
            "kyoto", "KYOTO", "京都", "Forests and temples. Deep emerald, maple red, quiet shadows.",
            contrast = 0.38f, saturation = 1.15f,
            shadowTint = floatArrayOf(-0.005f, 0.012f, 0.012f), highlightTint = floatArrayOf(0.02f, 0.012f, 0f),
            warmth = 0.08f, greenShift = 0.02f, warmSat = 0.18f, blueSat = 0.05f,
            grain = 0.25f, grainSize = 1.3f, halation = 0.05f, vignette = 0.26f, category = "MEISHO",
        ),
        FilmStock(
            "osaka", "OSAKA", "大阪", "Neon and canal reflections. Loud, warm and saturated.",
            contrast = 0.45f, saturation = 1.2f, fade = 0.01f,
            shadowTint = floatArrayOf(-0.015f, 0.01f, 0.04f), highlightTint = floatArrayOf(0.045f, 0.02f, -0.02f),
            warmth = -0.15f, warmSat = 0.25f, blueSat = 0.15f,
            grain = 0.4f, grainSize = 1.6f, halation = 0.6f, vignette = 0.28f, category = "MEISHO",
        ),
        FilmStock(
            "nara", "NARA", "奈良", "Parks, deer and morning light. Soft and warm.",
            contrast = 0.15f, saturation = 0.92f, fade = 0.04f,
            shadowTint = floatArrayOf(0f, 0.005f, 0.01f), highlightTint = floatArrayOf(0.03f, 0.02f, 0f),
            warmth = 0.18f, greenShift = -0.01f, warmSat = -0.03f,
            grain = 0.3f, grainSize = 1.4f, halation = 0.15f, vignette = 0.2f, category = "MEISHO",
        ),
        FilmStock(
            "kamakura", "KAMAKURA", "鎌倉", "Sea, shrines and grey skies. Muted and calm.",
            contrast = 0.3f, saturation = 0.78f, fade = 0.03f,
            shadowTint = floatArrayOf(0f, 0.01f, 0.02f), highlightTint = floatArrayOf(0.015f, 0.01f, 0.005f),
            warmth = -0.05f, greenShift = 0.03f, blueSat = -0.05f,
            grain = 0.38f, grainSize = 1.5f, halation = 0.04f, vignette = 0.22f, category = "MEISHO",
        ),
        FilmStock(
            "atami", "ATAMI", "熱海", "Old onsen town by a hazy sea. Faded amber, like a postcard.",
            contrast = 0.1f, saturation = 0.9f, fade = 0.08f,
            shadowTint = floatArrayOf(0.01f, 0.005f, 0f), highlightTint = floatArrayOf(0.04f, 0.025f, 0f),
            warmth = 0.25f, blueSat = -0.1f,
            grain = 0.45f, grainSize = 1.6f, halation = 0.3f, vignette = 0.3f, category = "MEISHO",
        ),
        FilmStock(
            "hakone", "HAKONE", "箱根", "Mist and mountains. Muted greens, a cool blue air.",
            contrast = 0.05f, saturation = 0.82f, fade = 0.06f,
            shadowTint = floatArrayOf(-0.005f, 0.01f, 0.025f), highlightTint = floatArrayOf(0.01f, 0.012f, 0.015f),
            warmth = -0.12f, greenShift = 0.02f, warmSat = 0.1f,
            grain = 0.3f, grainSize = 1.4f, halation = 0.06f, vignette = 0.18f, category = "MEISHO",
        ),
        FilmStock(
            "otaru", "OTARU", "小樽", "Snow, canals and gas lamps. Silver light, warm brick.",
            contrast = 0.3f, saturation = 0.9f,
            shadowTint = floatArrayOf(0f, 0.01f, 0.03f), highlightTint = floatArrayOf(0f, 0.005f, 0.01f),
            warmth = -0.2f, warmSat = 0.2f,
            grain = 0.25f, grainSize = 1.3f, halation = 0.25f, vignette = 0.2f, category = "MEISHO",
        ),
        FilmStock(
            "kanazawa", "KANAZAWA", "金沢", "Gardens, old streets and gold leaf. Rich, moss and gilt.",
            contrast = 0.4f, saturation = 1.0f, fade = 0.01f,
            shadowTint = floatArrayOf(0f, 0.008f, 0.012f), highlightTint = floatArrayOf(0.035f, 0.025f, 0f),
            warmth = 0.12f, greenShift = -0.01f, warmSat = 0.1f,
            grain = 0.3f, grainSize = 1.4f, halation = 0.1f, vignette = 0.3f, category = "MEISHO",
        ),
    )

    val all = stocks + meisho

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

    fun stock(id: String) = all.firstOrNull { it.id == id } ?: stocks.first()
    fun scene(id: String) = scenes.firstOrNull { it.id == id } ?: scenes.first()
}

/** Hand adjustments on top of a film. Also what a recipe stores. */
data class Adjust(
    val strength: Float = 1f,   // 0 = original, 1 = full film look
    val exposure: Float = 0f,   // stops, applied in the shader
    val warmth: Float = 0f,
    val grain: Float = 1f,      // multiplier
    val glow: Float = 0f,       // added halation
    val vignette: Float = 0f,   // added vignette
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("strength", strength.toDouble()).put("exposure", exposure.toDouble())
        .put("warmth", warmth.toDouble()).put("grain", grain.toDouble())
        .put("glow", glow.toDouble()).put("vignette", vignette.toDouble())

    companion object {
        val NONE = Adjust()
        fun fromJson(o: org.json.JSONObject?) = if (o == null) NONE else Adjust(
            o.optDouble("strength", 1.0).toFloat(), o.optDouble("exposure", 0.0).toFloat(),
            o.optDouble("warmth", 0.0).toFloat(), o.optDouble("grain", 1.0).toFloat(),
            o.optDouble("glow", 0.0).toFloat(), o.optDouble("vignette", 0.0).toFloat(),
        )
    }
}

/** A named, saved combination of a film and adjustments. */
data class Recipe(val id: String, val name: String, val stockId: String, val adjust: Adjust)

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
    val strength: Float = 1f,
    val exposure: Float = 0f,
) {
    companion object {
        fun of(stock: FilmStock, scene: Scene?, adjust: Adjust = Adjust.NONE): Look {
            val s = scene
            val a = adjust
            val m = stock.mixer
            // GL wants column-major; our mixer is written row-major
            val cm = floatArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])
            return Look(
                contrast = stock.contrast + (s?.contrast ?: 0f),
                saturation = (stock.saturation + (s?.saturation ?: 0f)).coerceAtLeast(0f),
                fade = stock.fade,
                shadowTint = stock.shadowTint,
                highlightTint = stock.highlightTint,
                warmth = stock.warmth + (s?.warmth ?: 0f) + a.warmth,
                tint = stock.tint,
                mixerColumnMajor = cm,
                hue = floatArrayOf(stock.greenShift, stock.warmSat, stock.blueSat),
                grain = stock.grain * (s?.grainMul ?: 1f) * a.grain,
                grainSize = stock.grainSize,
                halation = (stock.halation + (s?.halation ?: 0f) + a.glow).coerceIn(0f, 2f),
                vignette = (stock.vignette + (s?.vignette ?: 0f) + a.vignette).coerceIn(0f, 1f),
                mono = stock.mono,
                monoFilter = stock.monoFilter,
                strength = a.strength.coerceIn(0f, 1f),
                exposure = a.exposure,
            )
        }

        val DEFAULT = of(Films.stocks.first(), null)
    }
}
