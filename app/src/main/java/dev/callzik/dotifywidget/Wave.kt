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
 * Переливающийся фон в духе «Моей волны»: большие мягкие пятна цветов обложки
 * медленно дрейфуют и перетекают друг в друга. Виджет не может рисовать анимацию сам,
 * поэтому заранее рисуются [FRAMES] близких кадров замкнутого цикла, а ViewFlipper
 * непрерывно перетекает между ними: кроссфейд длиной во весь интервал, без пауз.
 */
object Wave {
    const val FRAMES = 12
    val ids = intArrayOf(
        R.id.wave0, R.id.wave1, R.id.wave2, R.id.wave3, R.id.wave4, R.id.wave5,
        R.id.wave6, R.id.wave7, R.id.wave8, R.id.wave9, R.id.wave10, R.id.wave11,
    )

    /** Интервал смены кадра: совпадает с длиной кроссфейда, чтобы движение не останавливалось. */
    const val CALM_MS = 1300
    const val LOUD_MS = 1100

    private const val PREFS = "settings"
    private const val KEY = "wave"

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()

    private var cacheKey: String? = null
    private var cache: List<Bitmap> = emptyList()

    // Плавная смена палитры при переключении трека: from → to за несколько шагов
    private var coverKey: Int? = null
    private var from: Colors? = null
    private var to: Colors? = null
    private var t = 1f

    val transitioning get() = t < 1f

    /** Следующий шаг перехода. Вызывается из [Widgets], пока [transitioning]. */
    fun step() {
        t = (t + 1f / STEPS).coerceAtMost(1f)
    }

    private const val STEPS = 6

    private fun current(): Colors? {
        val target = to ?: return null
        val start = from ?: return target
        if (t >= 1f) return target
        val e = t * t * (3 - 2 * t)
        return Colors(
            ColorUtils.blendARGB(start.base, target.base, e),
            target.blobs.indices.map { ColorUtils.blendARGB(start.blobs[it], target.blobs[it], e) },
        )
    }

    fun frames(context: Context, cover: Bitmap?, widthDp: Int, heightDp: Int, radiusDp: Float): List<Bitmap> {
        // Пятна размытые, поэтому кадры рисуются в пониженном разрешении: так легче для лаунчера
        val d = context.resources.displayMetrics.density * 0.3f
        val w = (widthDp * d).toInt().coerceAtLeast(8)
        val h = (heightDp * d).toInt().coerceAtLeast(8)
        val ck = cover?.let { System.identityHashCode(it) xor it.generationId } ?: 0
        if (ck != coverKey || to == null) {
            val next = palette(cover)
            from = current()
            to = next
            t = if (from == null) 1f else 0f
            coverKey = ck
        }
        val key = "$ck:$w:$h:$t"
        if (key == cacheKey) return cache

        val colors = current()!!
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
                    // Фигуры Лиссажу с целыми частотами: каждое пятно плывёт по своей петле,
                    // цикл замыкается без рывка, соседние кадры почти совпадают
                    val fx = FX[i]
                    val fy = FY[i]
                    val cx = w * (0.5f + 0.34f * cos(t * fx + i * 1.9f))
                    val cy = h * (0.5f + 0.42f * sin(t * fy + i * 2.7f))
                    val rad = maxOf(w, h) * (0.62f + 0.1f * sin(t + i * 1.3f))
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

    private val FX = intArrayOf(1, -1, 2, 1)
    private val FY = intArrayOf(1, 2, -1, -1)

    private class Colors(val base: Int, val blobs: List<Int>)

    private fun palette(cover: Bitmap?): Colors {
        val p = cover?.let { runCatching { Palette.from(it).maximumColorCount(24).generate() }.getOrNull() }
        val swatches = listOfNotNull(
            p?.vibrantSwatch, p?.lightVibrantSwatch, p?.mutedSwatch, p?.darkVibrantSwatch, p?.lightMutedSwatch, p?.dominantSwatch,
        ).map { it.rgb }.distinct()
        val blobs = (swatches.ifEmpty { listOf(0xFF7B4DFF.toInt(), 0xFFFF4F9A.toInt(), 0xFFFFA24C.toInt(), 0xFF3FB8FF.toInt()) })
            .take(4)
            .let { l -> List(4) { l[it % l.size] } }
        val c = if (p == null) 1f else CoverTone.colorfulness(p)
        if (c < CoverTone.MONO) {
            // Чёрно-белая обложка: графитовые пятна разной яркости с родным слабым оттенком
            val shades = floatArrayOf(0.56f, 0.36f, 0.72f, 0.26f)
            return Colors(CoverTone.mono(blobs[0], 0.07f), blobs.mapIndexed { i, b -> CoverTone.mono(b, shades[i]) })
        }
        val k = CoverTone.boostFactor(c)
        val base = ColorUtils.blendARGB(p?.getDarkMutedColor(0xFF140F24.toInt()) ?: 0xFF140F24.toInt(), Color.BLACK, 0.45f)
        return Colors(base, blobs.map { boost(it, k) })
    }

    /** Насыщенность повыше, как у «Моей волны». */
    private fun boost(c: Int, k: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(c, hsl)
        hsl[1] = (hsl[1] * k).coerceAtMost(0.95f)
        hsl[2] = hsl[2].coerceIn(0.42f, 0.62f)
        return ColorUtils.HSLToColor(hsl)
    }
}
