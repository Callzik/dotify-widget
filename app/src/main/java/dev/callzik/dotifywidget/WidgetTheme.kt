package dev.callzik.dotifywidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/** Оформления виджета. Выбор хранится в настройках приложения. */
enum class WidgetTheme(val label: String, val hint: String) {
    LIQUID("Liquid Glass", "Стекло как в iOS 26: блик, светящаяся кромка, стеклянные кнопки"),
    COVER("Под обложку", "Фон и акцент берутся из цветов обложки"),
    GLASS("Тёмное стекло", "Полупрозрачный тёмный фон, акцент из обложки"),
    LIGHT("Светлая", "Светлый фон, тёмный текст"),
    AMOLED("AMOLED", "Чистый чёрный и белый"),
    NEON("Неон", "Тёмно-фиолетовый фон, розовый неон"),
    CLEAR("Прозрачная", "Без фона, только обложка и текст");

    companion object {
        private const val PREFS = "settings"
        private const val KEY = "theme"

        fun current(context: Context): WidgetTheme {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return entries.firstOrNull { it.name == name } ?: COVER
        }

        fun save(context: Context, theme: WidgetTheme) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, theme.name).apply()
        }
    }
}

data class WidgetColors(
    val bg: Int,
    val accent: Int,
    val onAccent: Int,
    val fg: Int,
    val muted: Int,
    val track: Int,
    val overlay: Boolean,
    /** Liquid Glass: фон и кнопка рисуются стеклом через [Glass], а не заливкой. */
    val liquid: Boolean = false,
    val tint: Int = Color.TRANSPARENT,
)

object ThemeColors {
    private var cachedId = -1
    private var cached: Palette? = null

    private fun palette(cover: Bitmap?): Palette? {
        if (cover == null) return null
        val id = System.identityHashCode(cover) xor cover.generationId
        if (id != cachedId) {
            cached = runCatching { Palette.from(cover).maximumColorCount(16).generate() }.getOrNull()
            cachedId = id
        }
        return cached
    }

    fun of(theme: WidgetTheme, cover: Bitmap?): WidgetColors {
        val p = palette(cover)
        val vibrant = p?.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(DEFAULT_ACCENT)))
            ?: DEFAULT_ACCENT
        val accentOnDark = lift(vibrant)
        val white = Color.WHITE
        val whiteMuted = 0xB3FFFFFF.toInt()
        return when (theme) {
            WidgetTheme.COVER -> {
                val base = p?.getDarkMutedColor(p.getDarkVibrantColor(p.getDominantColor(DEFAULT_BG))) ?: DEFAULT_BG
                WidgetColors(darken(base), accentOnDark, onColor(accentOnDark), white, whiteMuted, 0x33FFFFFF, true)
            }
            WidgetTheme.GLASS ->
                WidgetColors(0xB814121C.toInt(), accentOnDark, onColor(accentOnDark), white, whiteMuted, 0x2EFFFFFF, true)
            WidgetTheme.LIGHT -> {
                val a = p?.getDarkVibrantColor(DEFAULT_ACCENT_DARK) ?: DEFAULT_ACCENT_DARK
                WidgetColors(0xFFF5F3F9.toInt(), a, onColor(a), 0xFF17151F.toInt(), 0xA017151F.toInt(), 0x2217151F, false)
            }
            WidgetTheme.AMOLED ->
                WidgetColors(Color.BLACK, white, Color.BLACK, white, 0x99FFFFFF.toInt(), 0x33FFFFFF, false)
            WidgetTheme.NEON ->
                WidgetColors(0xFF0E0B1C.toInt(), 0xFFFF3DCB.toInt(), Color.WHITE, white, 0xFF8FF3FF.toInt(), 0x33FF3DCB, true)
            WidgetTheme.LIQUID ->
                WidgetColors(Color.TRANSPARENT, white, white, white, 0xD9FFFFFF.toInt(), 0x47FFFFFF, false, liquid = true, tint = vibrant)
            WidgetTheme.CLEAR ->
                WidgetColors(Color.TRANSPARENT, white, Color.BLACK, white, 0xD9FFFFFF.toInt(), 0x40FFFFFF, false)
        }
    }

    private const val DEFAULT_BG = 0xFF1E1A2B.toInt()
    private const val DEFAULT_ACCENT = 0xFFB794FF.toInt()
    private const val DEFAULT_ACCENT_DARK = 0xFF5B34C9.toInt()

    private fun darken(c: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(c, hsl)
        hsl[2] = hsl[2].coerceAtMost(0.16f)
        hsl[1] = (hsl[1] * 1.1f).coerceAtMost(0.6f)
        return ColorUtils.HSLToColor(hsl)
    }

    /** Акцент должен читаться на тёмном фоне. */
    private fun lift(c: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(c, hsl)
        hsl[2] = hsl[2].coerceIn(0.58f, 0.75f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun onColor(c: Int) = if (ColorUtils.calculateLuminance(c) > 0.45) 0xFF111018.toInt() else Color.WHITE
}
