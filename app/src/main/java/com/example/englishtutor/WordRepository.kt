package com.example.englishtutor

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import kotlin.random.Random

enum class StudyFilter(val label: String) {
    SMART("תור חכם"),
    UNLEARNED("לא נלמדו"),
    HARD("קשות בלבד"),
    ALL("הכל")
}

data class WordCard(
    val english: String,
    val hebrew: String,
    var isLearned: Boolean = false,
    var isHard: Boolean = false,
    var streak: Int = 0,
    var timesSeen: Int = 0,
    var lastSeenTimestamp: Long = 0L
) {
    fun calculatePriorityScore(now: Long): Double {
        var score = 100.0
        if (isLearned) score -= 90.0
        if (isHard) score += 70.0
        if (timesSeen == 0) score += 40.0
        score -= (streak * 14.0).coerceAtMost(65.0)

        if (lastSeenTimestamp > 0L) {
            val minutesPassed = ((now - lastSeenTimestamp) / 60000.0).coerceIn(0.0, 240.0)
            score += minutesPassed * 0.45
        }

        score += Random.nextDouble(-5.0, 5.0)
        return score
    }
}

class WordRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_words_prefs", Context.MODE_PRIVATE)

    fun loadWords(): List<WordCard> {
        val wordList = mutableListOf<WordCard>()
        val seenKeys = HashSet<String>()

        try {
            val jsonString = context.assets.open("words.json").bufferedReader().use { reader ->
                reader.readText()
            }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val english = item.optString("english", "").trim()
                val hebrew = item.optString("hebrew", "").trim()

                // מונע טעינת מילים ריקות או כפולות
                if (english.isNotEmpty() && hebrew.isNotEmpty() && seenKeys.add(english.lowercase())) {
                    wordList.add(
                        WordCard(
                            english = english,
                            hebrew = hebrew,
                            isLearned = prefs.getBoolean("${english}_learned", false),
                            isHard = prefs.getBoolean("${english}_hard", false),
                            streak = prefs.getInt("${english}_streak", 0),
                            timesSeen = prefs.getInt("${english}_seen", 0),
                            lastSeenTimestamp = prefs.getLong("${english}_last", 0L)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            wordList.add(WordCard("Resilience", "חוסן נפשי / עמידות"))
        }
        return sortSmartQueue(wordList, StudyFilter.SMART)
    }

    fun saveWordState(word: WordCard) {
        prefs.edit().apply {
            putBoolean("${word.english}_learned", word.isLearned)
            putBoolean("${word.english}_hard", word.isHard)
            putInt("${word.english}_streak", word.streak)
            putInt("${word.english}_seen", word.timesSeen)
            putLong("${word.english}_last", word.lastSeenTimestamp)
            apply()
        }
    }

    fun sortSmartQueue(allWords: List<WordCard>, filter: StudyFilter): List<WordCard> {
        val now = System.currentTimeMillis()
        val filtered = when (filter) {
            StudyFilter.UNLEARNED -> allWords.filter { !it.isLearned }
            StudyFilter.HARD -> allWords.filter { it.isHard }
            StudyFilter.ALL -> allWords
            StudyFilter.SMART -> {
                val active = allWords.filter { !it.isLearned || it.isHard }
                active.ifEmpty { allWords }
            }
        }
        return filtered.sortedByDescending { it.calculatePriorityScore(now) }
    }

    fun pickNextSmartWord(
        allWords: List<WordCard>,
        filter: StudyFilter,
        currentEnglish: String?
    ): WordCard? {
        val queue = sortSmartQueue(allWords, filter)
        if (queue.isEmpty()) return null
        if (queue.size == 1) return queue.first()
        return queue.firstOrNull { !it.english.equals(currentEnglish, ignoreCase = true) }
            ?: queue.first()
    }
}
