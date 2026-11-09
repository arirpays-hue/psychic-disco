package com.example.englishtutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = WordRepository(applicationContext)

        setContent {
            val wordList = remember { repository.loadWords() }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DarkBg
            ) {
                FlashcardScreen(words = wordList, repository = repository)
            }
        }
    }
}
