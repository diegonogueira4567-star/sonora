package com.sonora.player

import android.os.SystemClock
import android.util.Log

/** Timing probes used to measure startup, scan and search (durations and counts only, no user data). */
object PerfLog {
    const val TAG = "SonoraPerf"

    /** Logs the time elapsed since the process started. */
    fun mark(label: String) {
        Log.i(TAG, "mark $label +${SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()} ms")
    }

    inline fun <T> measure(label: String, detail: (T) -> String = { "" }, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        val result = block()
        Log.i(TAG, "$label ${SystemClock.elapsedRealtime() - start} ms ${detail(result)}")
        return result
    }
}
