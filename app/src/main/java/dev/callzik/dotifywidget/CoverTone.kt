package dev.callzik.dotifywidget

import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/** Насколько обложка цветная: 0 — чёрно-белая, 1 — очень яркая. */
object CoverTone {
    /** Ниже этого порога обложка считается монохромной, и фон делается графитовым. */
    const val MONO = 0.18f

    fun colorfulness(p: Palette?): Float {
        val swatches = p?.swatches.orEmpty()
        if (swatches.isEmpty()) return 1f
        var sum = 0f
        var total = 0f
        for (s in swatches) {
            val hsl = s.hsl
            // У почти чёрных и почти белых пикселей насыщенность ничего не значит
            val l = hsl[2]
            val visible = if (l < 0.12f || l > 0.92f) 0f else hsl[1] * minOf(1f, minOf(l, 1f - l) * 4f)
            sum += visible * s.population
            total += s.population
        }
        return if (total > 0) sum / total else 1f
    }

    /** Усиление насыщенности пропорционально тому, насколько обложка цветная. */
    fun boostFactor(c: Float): Float {
        val t = ((c - MONO) / (0.45f - MONO)).coerceIn(0f, 1f)
        return 1f + 0.35f * t * t * (3 - 2 * t)
    }

    /** Графитовый оттенок того же цвета: родной тон, но почти без насыщенности. */
    fun mono(c: Int, lightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(c, hsl)
        hsl[1] = minOf(hsl[1], 0.1f)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }
}
