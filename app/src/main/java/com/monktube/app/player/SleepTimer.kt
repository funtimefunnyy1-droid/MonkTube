package com.monktube.app.player

import android.os.CountDownTimer
import androidx.media3.exoplayer.ExoPlayer

class SleepTimer(private val player: ExoPlayer) {
    private var timer: CountDownTimer? = null

    fun startTimer(minutes: Long, onFinishCallback: () -> Unit = {}) {
        timer?.cancel()
        val durationMillis = minutes * 60 * 1000
        timer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                player.pause()
                onFinishCallback()
            }
        }.start()
    }

    fun cancelTimer() {
        timer?.cancel()
        timer = null
    }
}
