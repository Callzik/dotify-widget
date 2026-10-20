package dev.callzik.dotifywidget

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock

/** Текущий плеер и его состояние. Заполняется сервисом [MediaListener]. */
object NowPlaying {
    var controller: MediaController? = null

    val title: String? get() = controller?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val artist: String?
        get() = controller?.metadata?.let {
            it.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: it.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        }
    val cover
        get() = controller?.metadata?.let {
            it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
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
