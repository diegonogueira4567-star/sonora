package com.sonora.player.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/** Custom session commands understood by [PlaybackService] (only accepted from this app). */
object PlaybackCommands {
    const val PLAY_NEXT = "sonora.PLAY_NEXT"
    const val ADD_TO_QUEUE = "sonora.ADD_TO_QUEUE"
    const val MOVE_IN_PLAY_ORDER = "sonora.MOVE_IN_PLAY_ORDER"
    const val CLEAR_UPCOMING = "sonora.CLEAR_UPCOMING"
    const val SET_SLEEP_TIMER = "sonora.SET_SLEEP_TIMER"
    const val CANCEL_SLEEP_TIMER = "sonora.CANCEL_SLEEP_TIMER"

    /** Broadcast from the service to controllers. */
    const val EVENT_MESSAGE = "sonora.EVENT_MESSAGE"

    const val ARG_TRACK_IDS = "trackIds"
    const val ARG_FROM = "from"
    const val ARG_TO = "to"
    const val ARG_MINUTES = "minutes"
    const val ARG_END_OF_TRACK = "endOfTrack"
    const val ARG_MESSAGE = "message"

    /** Session extras keys. */
    const val EXTRA_SLEEP_ENDS_AT = "sleepEndsAt"
    const val EXTRA_SLEEP_END_OF_TRACK = "sleepEndOfTrack"

    val ALL = listOf(PLAY_NEXT, ADD_TO_QUEUE, MOVE_IN_PLAY_ORDER, CLEAR_UPCOMING, SET_SLEEP_TIMER, CANCEL_SLEEP_TIMER)

    fun command(action: String) = SessionCommand(action, Bundle.EMPTY)
}
