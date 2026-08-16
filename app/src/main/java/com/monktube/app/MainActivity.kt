package com.monktube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.room.Room
import com.monktube.app.data.AppDatabase
import com.monktube.app.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "monktube.db"
        ).build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val historyList by db.historyDao().getAllHistory().collectAsState(initial = emptyList())

            MainScreen(
                historyList = historyList,
                onSearch = { /* Search logic */ },
                onVideoSelected = { /* Video click logic */ }
            )
        }
    }
}
