package dev.callzik.dotifywidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.RemoteViews

open class BaseWidget(private val layout: Int) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (NowPlaying.controller == null) NowPlaying.refresh(context)
        val views = Widgets.build(context, layout, javaClass)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Widgets.ACTION_PLAY_PAUSE -> Widgets.control(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            Widgets.ACTION_NEXT -> Widgets.control(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            Widgets.ACTION_PREV -> Widgets.control(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            else -> super.onReceive(context, intent)
        }
    }
}

class NowPlayingWidget : BaseWidget(R.layout.widget_full)
class CompactWidget : BaseWidget(R.layout.widget_compact)

object Widgets {
    const val ACTION_PLAY_PAUSE = "dev.callzik.dotifywidget.PLAY_PAUSE"
    const val ACTION_NEXT = "dev.callzik.dotifywidget.NEXT"
    const val ACTION_PREV = "dev.callzik.dotifywidget.PREV"

    private val providers = listOf(
        NowPlayingWidget::class.java to R.layout.widget_full,
        CompactWidget::class.java to R.layout.widget_compact,
    )

    fun updateAll(context: Context, progressOnly: Boolean = false) {
        if (NowPlaying.controller == null) NowPlaying.refresh(context)
        val manager = AppWidgetManager.getInstance(context)
        for ((cls, layout) in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) continue
            if (progressOnly) {
                val views = RemoteViews(context.packageName, layout)
                bindProgress(views)
                ids.forEach { manager.partiallyUpdateAppWidget(it, views) }
            } else {
                val views = build(context, layout, cls)
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        }
    }

    fun control(context: Context, keyCode: Int) {
        val c = NowPlaying.controller
        if (c != null) {
            val t = c.transportControls
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> t.skipToNext()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> t.skipToPrevious()
                else -> if (NowPlaying.isPlaying) t.pause() else t.play()
            }
        } else {
            // Нет доступа к сессиям: шлём медиакнопку, её поймает последний активный плеер
            val am = context.getSystemService(AudioManager::class.java)
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }

    fun build(
        context: Context,
        layout: Int,
        provider: Class<*>,
        theme: WidgetTheme = WidgetTheme.current(context),
    ): RemoteViews {
        val v = RemoteViews(context.packageName, layout)
        val cover = NowPlaying.cover
        val c = ThemeColors.of(theme, cover)
        val hasTrack = NowPlaying.controller != null && NowPlaying.title != null

        // Фон: непрозрачный цвет + отдельная прозрачность, иначе tint смешается с белым
        v.setInt(R.id.bg, "setColorFilter", c.bg or 0xFF000000.toInt())
        v.setInt(R.id.bg, "setImageAlpha", Color.alpha(c.bg))
        v.setViewVisibility(R.id.overlay, if (c.overlay) android.view.View.VISIBLE else android.view.View.GONE)

        if (cover != null) v.setImageViewBitmap(R.id.cover, rounded(cover, context))
        else v.setImageViewResource(R.id.cover, R.drawable.cover_placeholder)

        v.setTextViewText(R.id.title, if (hasTrack) NowPlaying.title else "Ничего не играет")
        v.setTextViewText(R.id.artist, if (hasTrack) NowPlaying.artist.orEmpty() else if (MediaListener.hasAccess(context)) "Включи трек в Dotify" else "Нажми, чтобы настроить")
        v.setTextColor(R.id.title, c.fg)
        v.setTextColor(R.id.artist, c.muted)
        if (layout == R.layout.widget_full) {
            v.setTextViewText(R.id.source, sourceLabel(context))
            v.setTextColor(R.id.source, c.muted)
            v.setTextColor(R.id.pos, c.muted)
            v.setTextColor(R.id.dur, c.muted)
            v.setInt(R.id.prev, "setColorFilter", c.fg)
        }
        v.setInt(R.id.next, "setColorFilter", c.fg)
        v.setInt(R.id.play_bg, "setColorFilter", c.accent)
        v.setInt(R.id.play, "setColorFilter", c.onAccent)
        v.setImageViewResource(R.id.play, if (NowPlaying.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

        if (Build.VERSION.SDK_INT >= 31) {
            v.setColorStateList(R.id.progress, "setProgressTintList", ColorStateList.valueOf(c.accent))
            v.setColorStateList(R.id.progress, "setProgressBackgroundTintList", ColorStateList.valueOf(c.track))
        }
        bindProgress(v)

        v.setOnClickPendingIntent(R.id.play_box, action(context, provider, ACTION_PLAY_PAUSE))
        v.setOnClickPendingIntent(R.id.next, action(context, provider, ACTION_NEXT))
        if (layout == R.layout.widget_full) v.setOnClickPendingIntent(R.id.prev, action(context, provider, ACTION_PREV))
        v.setOnClickPendingIntent(R.id.cover, openPlayer(context))
        v.setOnClickPendingIntent(R.id.info, openPlayer(context))
        return v
    }

    private fun bindProgress(v: RemoteViews) {
        val dur = NowPlaying.duration
        val pos = NowPlaying.position
        v.setProgressBar(R.id.progress, 1000, if (dur > 0) (pos * 1000 / dur).toInt() else 0, false)
        v.setTextViewText(R.id.pos, fmt(pos))
        v.setTextViewText(R.id.dur, fmt(dur))
    }

    private fun sourceLabel(context: Context): String {
        val pkg = NowPlaying.controller?.packageName ?: return "СЕЙЧАС ИГРАЕТ"
        val name = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("плеер")
        return "СЕЙЧАС ИГРАЕТ · ${name.uppercase()}"
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun action(context: Context, provider: Class<*>, act: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, act.hashCode() + provider.name.hashCode(),
            Intent(context, provider).setAction(act),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openPlayer(context: Context): PendingIntent {
        val pkg = NowPlaying.controller?.packageName
        val intent = (if (MediaListener.hasAccess(context)) pkg?.let { context.packageManager.getLaunchIntentForPackage(it) } else null)
            ?: Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 1, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Обложка со скруглёнными углами, уменьшенная, чтобы влезть в лимит RemoteViews. */
    private fun rounded(src: Bitmap, context: Context): Bitmap {
        val size = 320
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = size.toFloat() / minOf(src.width, src.height)
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(android.graphics.Matrix().apply {
                setScale(scale, scale)
                postTranslate((size - src.width * scale) / 2f, (size - src.height * scale) / 2f)
            })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        val r = size * 0.16f
        Canvas(out).drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, paint)
        return out
    }
}
