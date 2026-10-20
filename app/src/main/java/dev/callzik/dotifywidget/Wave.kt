package dev.callzik.dotifywidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Переливающийся фон в духе «Моей волны»: большие мягкие пятна цветов обложки,
 * которые плывут по кругу. Виджет не может рисовать анимацию сам, поэтому заранее
 * рисуются [FRAMES] кадров, а ViewFlipper плавно перетекает между ними.
 */
object Wave {
    const val FRAMES = 6
    val ids = intArrayOf(R.id.wave0, R.id.wave1, R.id.wave2, R.id.wave3, R.id.wave4, R.id.wave5)

    private const val PREFS = "settings"
    private const val KEY = "wave"

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()

    private var cacheKey: String? = null
    private var cache: List<Bitmap> = emptyList()

    fun frames(context: Context, cover: Bitmap?, widthDp: Int, heightDp: Int, radiusDp: Float): List<Bitmap> {
        // Пятна размытые, поэтому кадры рисуются в пониженном разрешении: так легче для лаунчера
        val d = context.resources.displayMetrics.density * 0.45f
        val w = (widthDp * d).toInt().coerceAtLeast(8)
        val h = (heightDp * d).toInt().coerceAtLeast(8)
        val key = "${cover?.let { System.identityHashCode(it) xor it.generationId }}:$w:$h"
        if (key == cacheKey) return cache

        val colors = palette(cover)
        val r = radiusDp * d
        val path = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        cache = List(FRAMES) { f ->
            val t = f.toFloat() / FRAMES * 2f * PI.toFloat()
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                val c = Canvas(bmp)
                c.clipPath(path)
                c.drawColor(colors.base)
                colors.blobs.forEachIndexed { i, color ->
                    // У каждого пятна своя орбита и скорость, кратная циклу: анимация замыкается без рывка
                    val speed = if (i % 2 == 0) 1 else -1
                    val a = t * speed + i * 1.7f
                    val cx = w * (0.5f + 0.38f * cos(a + i))
                    val cy = h * (0.5f + 0.45f * sin(a * 1f + i * 2.3f))
                    val rad = maxOf(w, h) * (0.55f + 0.12f * sin(t + i))
                    p.shader = RadialGradient(cx, cy, rad,
                        intArrayOf(color, ColorUtils.setAlphaComponent(color, 0x66), Color.TRANSPARENT),
                        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                }
            }
        }
        cacheKey = key
        return cache
    }

    private class Colors(val base: Int, val blobs: List<Int>)

    private fun palette(cover: Bitmap?): Colors {
        val p = cover?.let { runCatching { Palette.from(it).maximumColorCount(24).generate() }.getOrNull() }
        val swatches = listOfNotNull(
            p?.vibrantSwatch, p?.lightVibrantSwatch, p?.mutedSwatch, p?.darkVibrantSwatch, p?.lightMutedSwatch, p?.dominantSwatch,
        ).map { it.rgb }.distinct()
        val blobs = (swatches.ifEmpty { listOf(0xFF7B4DFF.toInt(), 0xFFFF4F9A.toInt(), 0xFFFFA24C.toInt(), 0xFF3FB8FF.toInt()) })
            .take(4).map { boost(it) }
        val base = ColorUtils.blendARGB(p?.getDarkMutedColor(0xFF140F24.toInt()) ?: 0xFF140F24.toInt(), Color.BLACK, 0.45f)
        return Colors(base, blobs)
    }

    /** Насыщенность повыше, как у «Моей волны». */
    private fun boost(c: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(c, hsl)
        hsl[1] = (hsl[1] * 1.35f).coerceIn(0.45f, 0.95f)
        hsl[2] = hsl[2].coerceIn(0.42f, 0.62f)
        return ColorUtils.HSLToColor(hsl)
    }
}
