package com.example.englishtutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = WordRepository(applicationContext)

        setContent {
            var wordList by remember { mutableStateOf<List<WordCard>?>(null) }

            LaunchedEffect(Unit) {
                wordList = withContext(Dispatchers.IO) {
                    repository.loadWords()
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DarkBg
            ) {
                val loaded = wordList
                if (loaded == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBg),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = TealNeon)
                    }
                } else {
                    FlashcardScreen(words = loaded, repository = repository)
                }
            }
        }
    }
}
