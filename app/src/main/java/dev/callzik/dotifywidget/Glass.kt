package dev.callzik.dotifywidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

/**
 * Liquid Glass в духе iOS 26. Виджеты Android не умеют размывать обои под собой,
 * поэтому стекло рисуется битмапом: лёгкая дымка, блик сверху, светящаяся кромка
 * с бликами по диагонали и едва заметный оттенок обложки.
 */
object Glass {

    private fun density(context: Context) = context.resources.displayMetrics.density.coerceAtMost(2.5f)

    fun panel(context: Context, widthDp: Int, heightDp: Int, radiusDp: Float, tint: Int): Bitmap {
        val d = density(context)
        val w = (widthDp * d).toInt().coerceAtLeast(8)
        val h = (heightDp * d).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        paint(Canvas(bmp), RectF(0f, 0f, w.toFloat(), h.toFloat()), radiusDp * d, d, tint)
        return bmp
    }

    fun circle(context: Context, sizeDp: Int, tint: Int): Bitmap {
        val d = density(context)
        val s = (sizeDp * d).toInt()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        paint(Canvas(bmp), RectF(0f, 0f, s.toFloat(), s.toFloat()), s / 2f, d, tint, button = true)
        return bmp
    }

    private fun paint(c: Canvas, r: RectF, radius: Float, d: Float, tint: Int, button: Boolean = false) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) }
        val w = r.width()
        val h = r.height()

        // Дымка: сверху чуть светлее, к низу прозрачнее
        p.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(if (button) 0x4DFFFFFF else 0x33FFFFFF, 0x14FFFFFF, 0x1FFFFFFF), null, Shader.TileMode.CLAMP)
        c.drawPath(path, p)

        // Цвет обложки, как будто просвечивает сквозь стекло
        p.shader = null
        p.color = ColorUtils.setAlphaComponent(tint, if (button) 0x30 else 0x1C)
        c.drawPath(path, p)

        c.save()
        c.clipPath(path)
        // Главный блик: широкая мягкая полоса по верхнему краю
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.42f, 0x3DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h * 0.42f, p)
        // Отражённый свет снизу справа, как у линзы
        p.shader = RadialGradient(w * 0.85f, h * 1.05f, maxOf(w, h) * 0.55f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        // Тень преломления по внутреннему краю, даёт объём
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f * d
        p.shader = LinearGradient(0f, 0f, 0f, h, 0x00000000, 0x14000000, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        c.restore()

        // Кромка: яркая в верхнем левом и нижнем правом углу, гаснет к середине
        val rim = 1.3f * d
        val inset = RectF(r.left + rim / 2, r.top + rim / 2, r.right - rim / 2, r.bottom - rim / 2)
        val rr = (radius - rim / 2).coerceAtLeast(0f)
        p.strokeWidth = rim
        p.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(0xF2FFFFFF.toInt(), 0x47FFFFFF, 0x0FFFFFFF, 0x38FFFFFF, 0xB3FFFFFF.toInt()),
            floatArrayOf(0f, 0.22f, 0.5f, 0.78f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(inset, rr, rr, p)

        // Вторая, внутренняя кромка: толщина стекла
        val i2 = RectF(inset.left + 1.6f * d, inset.top + 1.6f * d, inset.right - 1.6f * d, inset.bottom - 1.6f * d)
        val r2 = (rr - 1.6f * d).coerceAtLeast(0f)
        p.strokeWidth = 1f * d
        p.shader = LinearGradient(0f, 0f, 0f, h, 0x26FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(i2, r2, r2, p)
    }
}
