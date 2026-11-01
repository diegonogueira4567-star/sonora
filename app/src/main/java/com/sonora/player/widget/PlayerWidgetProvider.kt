package com.sonora.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.widget.RemoteViews
import com.sonora.player.MainActivity
import com.sonora.player.R
import com.sonora.player.playback.PlaybackService

/** Home-screen widget showing the current track with play/pause, previous and next. */
class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, last)
    }

    data class WidgetState(val title: String?, val artist: String?, val playing: Boolean, val art: Bitmap?)

    companion object {
        @Volatile
        private var last = WidgetState(null, null, false, null)

        fun update(context: Context, title: String?, artist: String?, playing: Boolean, art: Bitmap?) {
            last = WidgetState(title, artist, playing, art)
            render(context, last)
        }

        private fun render(context: Context, state: WidgetState) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_player).apply {
                setTextViewText(R.id.widget_title, state.title ?: context.getString(R.string.widget_nothing_playing))
                setTextViewText(R.id.widget_artist, state.artist ?: context.getString(R.string.widget_open_app))
                if (state.art != null) setImageViewBitmap(R.id.widget_art, state.art) else setImageViewResource(R.id.widget_art, R.drawable.ic_widget_note)
                setImageViewResource(R.id.widget_play_pause, if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                val open = PendingIntent.getActivity(
                    context, 10, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                setOnClickPendingIntent(R.id.widget_root, open)
                setOnClickPendingIntent(R.id.widget_art, open)
                setOnClickPendingIntent(
                    R.id.widget_play_pause,
                    mediaButton(context, if (state.playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY, 11, foreground = !state.playing),
                )
                setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT, 12, foreground = false))
                setOnClickPendingIntent(R.id.widget_prev, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 13, foreground = false))
            }
            manager.updateAppWidget(ids, views)
        }

        /**
         * Same mechanism as the media notification: a media-button intent delivered to the
         * playback service. Starting playback uses a foreground-service start so Android 12+
         * background limits (and Android 15 audio-focus rules) are respected.
         */
        private fun mediaButton(context: Context, keyCode: Int, code: Int, foreground: Boolean): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setClass(context, PlaybackService::class.java)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (foreground) PendingIntent.getForegroundService(context, code, intent, flags)
            else PendingIntent.getService(context, code, intent, flags)
        }
    }
}
