package com.monktube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import com.monktube.app.data.AppDatabase
import com.monktube.app.data.HistoryItem
import com.monktube.app.network.NewPipeHelper
import com.monktube.app.player.SleepTimer
import com.monktube.app.ui.MainScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var sleepTimer: SleepTimer
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build()
        sleepTimer = SleepTimer(player)
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "monktube-db")
            .fallbackToDestructiveMigration()
            .build()

        setContent {
            val historyList by db.historyDao().getAllHistory().collectAsState(initial = emptyList())

            MainScreen(
                historyList = historyList,
                onSearch = { query ->
                    playVideo(query)
                },
                onVideoSelected = { videoId ->
                    playVideo(videoId)
                }
            )
        }
    }

    private fun playVideo(videoId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val streamUrl = NewPipeHelper.getStreamUrl(videoId)

                if (!streamUrl.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        val mediaItem = MediaItem.fromUri(streamUrl)
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                        sleepTimer.startTimer(30)
                    }

                    db.historyDao().insert(
                        HistoryItem(
                            videoId = videoId,
                            title = "Video: $videoId",
                            channel = "YouTube Stream",
                            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        sleepTimer.cancelTimer()
    }
}
