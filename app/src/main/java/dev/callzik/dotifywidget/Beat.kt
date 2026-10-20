package dev.callzik.dotifywidget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer

/**
 * Реакция фона на музыку. Visualizer на общем выходе отдаёт только уровень громкости
 * (RMS), звук никуда не пишется. Опрос ~3 раза в секунду, чтобы не тратить батарею.
 */
object Beat {
    private const val PREFS = "settings"
    private const val KEY = "beat"

    fun enabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false) && hasPermission(context)

    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private var viz: Visualizer? = null
    private val m = Visualizer.MeasurementPeakRms()
    private var level = 0f

    fun start(): Boolean {
        if (viz != null) return true
        viz = runCatching {
            Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
                enabled = true
            }
        }.getOrNull()
        return viz != null
    }

    fun stop() {
        runCatching { viz?.release() }
        viz = null
        level = 0f
    }

    /** 0..1: быстрый подъём на ударе, плавный спад. */
    fun level(): Float {
        val v = viz ?: return 0f
        if (runCatching { v.getMeasurementPeakRms(m) }.getOrDefault(-1) != Visualizer.SUCCESS) return 0f
        val x = ((m.mRms + 4800) / 4000f).coerceIn(0f, 1f)
        level = maxOf(x, level * 0.55f)
        return level
    }
}
