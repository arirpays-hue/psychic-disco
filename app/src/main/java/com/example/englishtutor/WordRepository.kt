package com.example.englishtutor

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import kotlin.math.max
import kotlin.math.min
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
    var hardCount: Int = 0,
    var easinessFactor: Float = 2.5f,
    var lastSeenTimestamp: Long = 0L
) {
    // חישוב ציון חכם משולב עקומת שכחה (SM-2)
    fun calculatePriorityScore(now: Long, isRecentlySeen: Boolean): Double {
        var score = 100.0

        // מניעת חזרה מיידית על אותן מילים שהוצגו ב-4 הכרטיסים האחרונים
        if (isRecentlySeen) {
            score -= 250.0
        }

        // מילים קשות מקבלות עדיפות גבוהה מאוד לפי מספר הפעמים שסומנו כקשות
        if (isHard) {
            score += 85.0 + (hardCount * 15.0).coerceAtMost(60.0)
        }

        // מילים שנלמדו עוברות לסוף התור אך חוזרות בהדרגה לבדיקת זיכרון ארוך-טווח
        if (isLearned) {
            score -= 120.0
        }

        // איזון בין מילים בתהליך למידה (1-3 צפיות) לבין מילים חדשות לגמרי
        when {
            timesSeen == 0 -> score += 45.0
            timesSeen in 1..2 && !isLearned -> score += 55.0 // חיזוק זיכרון קצר-טווח
        }

        // הפחתת עדיפות לפי רצף הצלחות ומקדם קלות אישי של המילה
        score -= (streak * 16.0 * (easinessFactor / 2.5)).coerceAtMost(80.0)

        // חישוב עקומת שכחה לפי הזמן שחלף מאז הצפייה האחרונה
        if (lastSeenTimestamp > 0L) {
            val minutesPassed = ((now - lastSeenTimestamp) / 60000.0).coerceIn(0.0, 360.0)
            // מרווח אידיאלי בדקות גדל אקספוננציאלית עם הרצף
            val idealIntervalMinutes = if (isHard) 2.0 else (streak + 1) * easinessFactor * 3.0
            val overdueRatio = (minutesPassed / idealIntervalMinutes.coerceAtLeast(1.0)).coerceAtMost(4.0)
            score += overdueRatio * 25.0
        }

        // רעש קל לגיוון הסדר בין מילים בעלות ניקוד זהה
        score += Random.nextDouble(-3.5, 3.5)
        return score
    }
}

class WordRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_words_prefs", Context.MODE_PRIVATE)

    // זוכר את המילים האחרונות שהוצגו כדי למנוע חזרה מעיקה על אותה מילה ברצף
    private val recentHistory = ArrayDeque<String>(6)

    fun loadWords(): List<WordCard> {
        val wordList = mutableListOf<WordCard>()
        val seenKeys = HashSet<String>()

        try {
            val jsonString = context.assets.open("words.json").bufferedReader().use { reader ->
                reader.readText()
            }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val english = item.optString("english", "").trim()
                val hebrew = item.optString("hebrew", "").trim()

                if (english.isNotEmpty() && hebrew.isNotEmpty() && seenKeys.add(english.lowercase())) {
                    wordList.add(
                        WordCard(
                            english = english,
                            hebrew = hebrew,
                            isLearned = prefs.getBoolean("${english}_learned", false),
                            isHard = prefs.getBoolean("${english}_hard", false),
                            streak = prefs.getInt("${english}_streak", 0),
                            timesSeen = prefs.getInt("${english}_seen", 0),
                            hardCount = prefs.getInt("${english}_hard_count", 0),
                            easinessFactor = prefs.getFloat("${english}_ef", 2.5f),
                            lastSeenTimestamp = prefs.getLong("${english}_last", 0L)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (wordList.isEmpty()) {
            wordList.add(WordCard("Resilience", "חוסן נפשי / עמידות"))
        }
        return sortSmartQueue(wordList, StudyFilter.SMART)
    }

    // מעדכן את פרמטרי האלגוריתם החכם (SM-2) ושומר בזיכרון המכשיר
    fun recordReview(word: WordCard, learned: Boolean? = null, hard: Boolean? = null) {
        word.timesSeen++
        word.lastSeenTimestamp = System.currentTimeMillis()

        if (learned != null) {
            word.isLearned = learned
            if (learned) {
                word.streak++
                word.isHard = false
                word.easinessFactor = min(3.0f, word.easinessFactor + 0.15f)
            } else {
                word.streak = 0
                word.easinessFactor = max(1.3f, word.easinessFactor - 0.2f)
            }
        }

        if (hard != null) {
            word.isHard = hard
            if (hard) {
                word.streak = 0
                word.isLearned = false
                word.hardCount++
                word.easinessFactor = max(1.3f, word.easinessFactor - 0.3f)
            }
        }

        saveWordState(word)
    }

    fun saveWordState(word: WordCard) {
        try {
            prefs.edit().apply {
                putBoolean("${word.english}_learned", word.isLearned)
                putBoolean("${word.english}_hard", word.isHard)
                putInt("${word.english}_streak", word.streak)
                putInt("${word.english}_seen", word.timesSeen)
                putInt("${word.english}_hard_count", word.hardCount)
                putFloat("${word.english}_ef", word.easinessFactor)
                putLong("${word.english}_last", word.lastSeenTimestamp)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sortSmartQueue(allWords: List<WordCard>, filter: StudyFilter): List<WordCard> {
        if (allWords.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val recentSet = recentHistory.toSet()

        val filtered = when (filter) {
            StudyFilter.UNLEARNED -> allWords.filter { !it.isLearned }
            StudyFilter.HARD -> allWords.filter { it.isHard }
            StudyFilter.ALL -> allWords
            StudyFilter.SMART -> {
                val active = allWords.filter { !it.isLearned || it.isHard }
                active.ifEmpty { allWords }
            }
        }

        return filtered.sortedByDescending { card ->
            card.calculatePriorityScore(now, recentSet.contains(card.english.lowercase()))
        }
    }

    fun pickNextSmartWord(
        allWords: List<WordCard>,
        filter: StudyFilter,
        currentEnglish: String?
    ): WordCard? {
        if (!currentEnglish.isNullOrBlank()) {
            val key = currentEnglish.lowercase()
            recentHistory.remove(key)
            recentHistory.addLast(key)
            while (recentHistory.size > 4) {
                recentHistory.removeFirst()
            }
        }

        val queue = sortSmartQueue(allWords, filter)
        if (queue.isEmpty()) return null
        if (queue.size == 1) return queue.first()

        val picked = queue.firstOrNull { !it.english.equals(currentEnglish, ignoreCase = true) }
            ?: queue.first()

        return picked
    }
}
