package dev.callzik.dotifywidget

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.URL

/** Текущий плеер и его состояние. Заполняется сервисом [MediaListener]. */
object NowPlaying {
    var controller: MediaController? = null

    val title: String? get() = controller?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val artist: String?
        get() = controller?.metadata?.let {
            it.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: it.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        }
    /**
     * Обложка из всех мест, где её кладут плееры: битмап в метаданных, ссылка в метаданных
     * (догружается в фоне), картинка в медиауведомлении.
     */
    val cover: Bitmap?
        get() {
            val m = controller?.metadata ?: return null
            return m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                ?: uriCover.takeIf { uriKey != null && uriKey == coverUri }
                ?: notifCover.takeIf { notifPackage == controller?.packageName && (notifTitle == null || notifTitle == title) }
        }

    private val coverUri: String?
        get() = controller?.metadata?.let {
            it.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: it.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: it.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        }?.takeIf { it.isNotBlank() }

    @Volatile private var uriKey: String? = null
    @Volatile private var uriCover: Bitmap? = null

    var notifCover: Bitmap? = null
    var notifTitle: String? = null
    var notifPackage: String? = null

    private val main = Handler(Looper.getMainLooper())

    /** Если обложка пришла только ссылкой, скачивает её и обновляет виджеты. */
    fun loadCoverUri(context: Context) {
        val u = coverUri ?: return
        if (u == uriKey) return
        uriKey = u
        uriCover = null
        val app = context.applicationContext
        Thread {
            val bmp = runCatching { decode(app, u) }.getOrNull()
            main.post {
                if (uriKey == u && bmp != null) {
                    uriCover = bmp
                    Widgets.updateAll(app)
                }
            }
        }.start()
    }

    private fun decode(context: Context, u: String): Bitmap? {
        val uri = Uri.parse(u)
        val open = {
            if (uri.scheme == "http" || uri.scheme == "https") URL(u).openStream()
            else context.contentResolver.openInputStream(uri)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
        return open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }
    val duration: Long get() = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val isPlaying: Boolean get() = controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    val position: Long
        get() {
            val s = controller?.playbackState ?: return 0L
            if (s.state != PlaybackState.STATE_PLAYING) return s.position
            val elapsed = SystemClock.elapsedRealtime() - s.lastPositionUpdateTime
            return (s.position + elapsed * s.playbackSpeed).toLong().coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        }

    /** Dotify в приоритете, затем то, что сейчас играет, затем любой плеер. */
    fun pick(controllers: List<MediaController>): MediaController? =
        controllers.firstOrNull { it.packageName.contains("dotify", true) && it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.packageName.contains("dotify", true) }
            ?: controllers.firstOrNull()

    /** Подхватывает сессии напрямую, если процесс перезапустился, а сервис уже подключён. */
    fun refresh(context: Context) {
        if (!MediaListener.hasAccess(context)) return
        runCatching {
            val msm = context.getSystemService(MediaSessionManager::class.java)
            controller = pick(msm.getActiveSessions(ComponentName(context, MediaListener::class.java)))
        }
    }
}
