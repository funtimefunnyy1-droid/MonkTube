package com.monktube.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.monktube.app.data.AppDatabase
import com.monktube.app.data.HistoryItem
import com.monktube.app.network.NewPipeHelper
import com.monktube.app.ui.MainScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val db by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "monktube.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val historyList by db.historyDao().getAllHistory().collectAsState(initial = emptyList())

            MainScreen(
                historyList = historyList,
                onSearch = { query -> handleSearch(query) },
                onVideoSelected = { videoId -> playVideo(videoId) }
            )
        }
    }

    private fun handleSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        // Check if query is directly a YouTube video ID or link
        val videoId = when {
            trimmed.contains("v=") -> trimmed.substringAfter("v=").substringBefore("&")
            trimmed.contains("youtu.be/") -> trimmed.substringAfter("youtu.be/").substringBefore("?")
            trimmed.length == 11 -> trimmed
            else -> trimmed
        }

        playVideo(videoId)
    }

    private fun playVideo(videoId: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Loading stream...", Toast.LENGTH_SHORT).show()
                val streamUrl = NewPipeHelper.getStreamUrl(videoId)

                if (!streamUrl.isNullOrEmpty()) {
                    // Record in local history
                    withContext(Dispatchers.IO) {
                        db.historyDao().insert(
                            HistoryItem(
                                videoId = videoId,
                                title = "Video ($videoId)",
                                channel = "YouTube",
                                thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                            )
                        )
                    }

                    // Open native player intent
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(streamUrl), "video/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Could not resolve stream URL", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
