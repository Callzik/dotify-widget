package dev.callzik.dotifywidget

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService

/** Доступ к уведомлениям нужен системе, чтобы отдать нам список играющих плееров. */
class MediaListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private var watched: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(NowPlaying.pick(list.orEmpty()))
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = Widgets.updateAll(this@MediaListener)
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Если этот плеер встал на паузу, а другой играет, переключаемся на другой
            msm?.let { attach(NowPlaying.pick(it.getActiveSessions(component()))) }
            Widgets.updateAll(this@MediaListener)
            scheduleTick()
        }
        override fun onSessionDestroyed() {
            msm?.let { attach(NowPlaying.pick(it.getActiveSessions(component()))) }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            Widgets.updateAll(this@MediaListener, progressOnly = true)
            if (NowPlaying.isPlaying) handler.postDelayed(this, 1000)
        }
    }

    private val beat = object : Runnable {
        override fun run() {
            if (!NowPlaying.isPlaying || !Beat.enabled(this@MediaListener) || !Beat.start()) {
                Beat.stop()
                Widgets.pulse(this@MediaListener, null)
                return
            }
            Widgets.pulse(this@MediaListener, Beat.level())
            handler.postDelayed(this, 330)
        }
    }

    private fun component() = ComponentName(this, MediaListener::class.java)

    override fun onListenerConnected() {
        instance = this
        msm = getSystemService(MediaSessionManager::class.java).also {
            it.addOnActiveSessionsChangedListener(sessionsListener, component())
            attach(NowPlaying.pick(it.getActiveSessions(component())))
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        handler.removeCallbacks(beat)
        Beat.stop()
        msm?.removeOnActiveSessionsChangedListener(sessionsListener)
        watched?.unregisterCallback(callback)
        watched = null
        handler.removeCallbacks(tick)
    }

    private fun attach(c: MediaController?) {
        if (c?.sessionToken != watched?.sessionToken) {
            watched?.unregisterCallback(callback)
            watched = c
            c?.registerCallback(callback, handler)
        }
        NowPlaying.controller = c
        Widgets.updateAll(this)
        scheduleTick()
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        if (NowPlaying.isPlaying) handler.postDelayed(tick, 1000)
        refreshBeat()
    }

    fun refreshBeat() {
        handler.removeCallbacks(beat)
        handler.post(beat)
    }

    companion object {
        var instance: MediaListener? = null

        fun hasAccess(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, MediaListener::class.java).flattenToString()
            return flat.split(':').any { it == me }
        }
    }
}
