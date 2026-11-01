package com.sonora.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * Wraps the ExoPlayer exposed to the media session so that shuffle always starts from the item the
 * user picked and plays every other item afterwards (ExoPlayer's default random order may place
 * items "before" the starting one, which would then never play without repeat).
 */
@OptIn(UnstableApi::class)
class SonoraPlayer(val exoPlayer: ExoPlayer) : ForwardingPlayer(exoPlayer) {

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
        reshuffleFromCurrent()
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        super.setMediaItems(mediaItems, resetPosition)
        reshuffleFromCurrent()
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled && !exoPlayer.shuffleModeEnabled) {
            applyShuffleOrder(ShuffleOrders.startingWith(exoPlayer.mediaItemCount, exoPlayer.currentMediaItemIndex))
        }
        super.setShuffleModeEnabled(shuffleModeEnabled)
    }

    fun reshuffleFromCurrent() {
        if (exoPlayer.shuffleModeEnabled && exoPlayer.mediaItemCount > 0) {
            applyShuffleOrder(ShuffleOrders.startingWith(exoPlayer.mediaItemCount, exoPlayer.currentMediaItemIndex))
        }
    }

    fun currentPlayOrder(): IntArray = ShuffleOrders.playOrder(exoPlayer.currentTimeline, exoPlayer.shuffleModeEnabled)

    /** Order of timeline indexes when shuffle is on (kept even while shuffle is off). */
    fun shuffledOrder(): IntArray = ShuffleOrders.playOrder(exoPlayer.currentTimeline, true)

    fun applyShuffleOrder(order: IntArray) {
        if (ShuffleOrders.isValidPermutation(order, exoPlayer.mediaItemCount)) {
            exoPlayer.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order, Random.nextLong()))
        }
    }
}
