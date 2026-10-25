package com.example.bedrockclient

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator

/** State alat bantu (stopwatch & countdown). Tampil di HUD saat aktif. */
object ToolState {
    var swRunning = false; private set
    private var swStart = 0L; private var swAcc = 0L
    fun swElapsed() = swAcc + if (swRunning) SystemClock.elapsedRealtime() - swStart else 0L
    fun swActive() = swRunning || swAcc > 0
    fun swToggle() {
        if (swRunning) { swAcc += SystemClock.elapsedRealtime() - swStart; swRunning = false }
        else { swStart = SystemClock.elapsedRealtime(); swRunning = true }
    }
    fun swReset() { swRunning = false; swAcc = 0 }

    var cdMinutes = 5
    var cdRunning = false; private set
    private var cdEnd = 0L
    fun cdStart() { cdEnd = SystemClock.elapsedRealtime() + cdMinutes * 60_000L; cdRunning = true }
    fun cdStop() { cdRunning = false }
    fun cdRemaining() = if (cdRunning) maxOf(0L, cdEnd - SystemClock.elapsedRealtime()) else 0L

    /** Dipanggil periodik; true saat countdown baru saja selesai. */
    fun checkDone(c: Context, vibrate: Boolean): Boolean {
        if (cdRunning && cdRemaining() == 0L) { cdRunning = false; if (vibrate) vibrate(c); return true }
        return false
    }

    // Sesi bermain: dimulai saat game pertama kali tampil setelah peluncuran.
    var sessionStart = 0L
    fun sessionElapsed() = if (sessionStart == 0L) 0L else SystemClock.elapsedRealtime() - sessionStart
    fun fmtHms(ms: Long): String { val t = ms / 1000; return "%02d:%02d:%02d".format(t / 3600, (t / 60) % 60, t % 60) }
    fun fmt(ms: Long, tenths: Boolean): String {
        val t = ms / 1000
        return if (tenths) "%02d:%02d.%d".format(t / 60, t % 60, (ms % 1000) / 100) else "%02d:%02d".format(t / 60, t % 60)
    }
    private fun vibrate(c: Context) {
        try {
            val v = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pat = longArrayOf(0, 300, 150, 300)
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pat, -1)) else @Suppress("DEPRECATION") v.vibrate(pat, -1)
        } catch (_: Exception) {}
    }
}
