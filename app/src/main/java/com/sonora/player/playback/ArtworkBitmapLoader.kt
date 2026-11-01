package com.sonora.player.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.sonora.player.data.media.ArtworkLoader
import java.io.IOException
import java.util.concurrent.Executors

/** Supplies embedded album art to the notification, lock screen and system media controls. */
@OptIn(UnstableApi::class)
class ArtworkBitmapLoader(private val artworkLoader: ArtworkLoader) : BitmapLoader {
    private val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: throw IOException("Imagem inválida")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        artworkLoader.loadBlocking(uri.toString(), ART_SIZE_PX) ?: throw IOException("Sem capa")
    }

    fun release() = executor.shutdown()

    private companion object {
        const val ART_SIZE_PX = 512
    }
}
