package com.sonora.player

import android.content.Context
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

val targetContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
val app: SonoraApp get() = targetContext.applicationContext as SonoraApp

@Suppress("UNCHECKED_CAST")
fun <T> onMain(block: () -> T): T {
    var result: Any? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
    return result as T
}

fun waitUntil(timeoutMs: Long = 10_000, message: String = "condição", condition: () -> Boolean) {
    val end = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < end) {
        if (onMain(condition)) return
        Thread.sleep(50)
    }
    throw AssertionError("Tempo esgotado esperando: $message")
}

/** Writes a real PCM WAV file (sine tone) that ExoPlayer decodes. */
fun writeToneWav(file: File, seconds: Double, freq: Double = 440.0): File {
    val rate = 44100
    val samples = (rate * seconds).toInt()
    val data = ByteArray(samples * 2)
    for (i in 0 until samples) {
        val v = (8000 * sin(2 * PI * freq * i / rate)).toInt()
        data[2 * i] = (v and 0xff).toByte()
        data[2 * i + 1] = ((v shr 8) and 0xff).toByte()
    }
    RandomAccessFile(file, "rw").use { f ->
        f.setLength(0)
        fun intLE(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        fun shortLE(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
        f.write("RIFF".toByteArray()); f.write(intLE(36 + data.size)); f.write("WAVE".toByteArray())
        f.write("fmt ".toByteArray()); f.write(intLE(16)); f.write(shortLE(1)); f.write(shortLE(1))
        f.write(intLE(rate)); f.write(intLE(rate * 2)); f.write(shortLE(2)); f.write(shortLE(16))
        f.write("data".toByteArray()); f.write(intLE(data.size)); f.write(data)
    }
    return file
}

/** UiAutomation.grantRuntimePermission only exists on Android 9+; older versions use `pm grant`. */
fun grantAudioPermission() {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val permission = com.sonora.player.ui.permissions.AUDIO_PERMISSION
    if (android.os.Build.VERSION.SDK_INT >= 28) {
        automation.grantRuntimePermission(targetContext.packageName, permission)
    } else {
        automation.executeShellCommand("pm grant ${targetContext.packageName} $permission").close()
        Thread.sleep(300)
    }
}

fun writeCorruptFile(file: File): File = file.apply { writeBytes(Random(42).nextBytes(32 * 1024)) }
