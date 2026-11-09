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
    var timesSeen: Int = 0,
    var hardCount: Int = 0,
    var scheduledAtStep: Int = 0,
    var lastSeenTimestamp: Long = 0L
) {
    fun calculatePriorityScore(currentStep: Int, isRecentlySeen: Boolean): Double {
        // מילים שנלמדו מקבלות ציון שלילי קיצוני כדי שלעולם לא ייבחרו בתור הלמידה
        if (isLearned) return -100000.0

        var score = 100.0

        if (isHard) {
            if (currentStep >= scheduledAtStep) {
                // הגיע הזמן להחזיר את המילה הקשה! קופצת לראש התור
                val overdueSteps = (currentStep - scheduledAtStep).coerceAtMost(20)
                score += 450.0 + (overdueSteps * 25.0) + (hardCount * 20.0)
            } else {
                // המילה סומנה כקשה ממש עכשיו - ממתינה עוד כמה כרטיסים לפני שתחזור
                score -= 220.0
            }
        } else {
            // מילים שלא נלמדו: תעדוף למילים שטרם נראו או שנראו מעט
            if (timesSeen == 0) {
                score += 55.0
            } else {
                score += (35.0 - timesSeen * 5.0).coerceAtLeast(5.0)
            }
        }

        // מונע חזרה מיידית על אותה מילה כרטיס אחרי כרטיס (אלא אם הגיע מועד החזרה שלה)
        if (isRecentlySeen && !(isHard && currentStep >= scheduledAtStep)) {
            score -= 320.0
        }

        score += Random.nextDouble(-6.0, 6.0)
        return score
    }
}

class WordRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_words_prefs", Context.MODE_PRIVATE)

    private val recentHistory = ArrayDeque<String>(6)
    private var stepCounter: Int = prefs.getInt("global_step_counter", 0)

    @Synchronized
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
                            timesSeen = prefs.getInt("${english}_seen", 0),
                            hardCount = prefs.getInt("${english}_hard_count", 0),
                            scheduledAtStep = prefs.getInt("${english}_due_step", 0),
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
        return wordList
    }

    @Synchronized
    fun recordReview(word: WordCard, learned: Boolean? = null, hard: Boolean? = null) {
        stepCounter++
        word.timesSeen++
        word.lastSeenTimestamp = System.currentTimeMillis()

        when {
            learned == true -> {
                // סומן כנלמד -> לא יחזור לעולם!
                word.isLearned = true
                word.isHard = false
                word.scheduledAtStep = Int.MAX_VALUE
            }
            learned == false -> {
                word.isLearned = false
            }
        }

        when {
            hard == true -> {
                // סומן כקשה -> מתוזמן לחזור אוטומטית בעוד 3 עד 5 מילים
                word.isHard = true
                word.isLearned = false
                word.hardCount++
                val delaySteps = Random.nextInt(3, 6)
                word.scheduledAtStep = stepCounter + delaySteps
            }
            hard == false -> {
                word.isHard = false
            }
        }

        saveWordState(word)
    }

    @Synchronized
    fun saveWordState(word: WordCard) {
        try {
            prefs.edit().apply {
                putInt("global_step_counter", stepCounter)
                putBoolean("${word.english}_learned", word.isLearned)
                putBoolean("${word.english}_hard", word.isHard)
                putInt("${word.english}_seen", word.timesSeen)
                putInt("${word.english}_hard_count", word.hardCount)
                putInt("${word.english}_due_step", word.scheduledAtStep)
                putLong("${word.english}_last", word.lastSeenTimestamp)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun resetAllProgress(allWords: List<WordCard>) {
        try {
            stepCounter = 0
            recentHistory.clear()
            prefs.edit().clear().apply()
            allWords.forEach { word ->
                word.isLearned = false
                word.isHard = false
                word.timesSeen = 0
                word.hardCount = 0
                word.scheduledAtStep = 0
                word.lastSeenTimestamp = 0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun sortSmartQueue(allWords: List<WordCard>, filter: StudyFilter): List<WordCard> {
        if (allWords.isEmpty()) return emptyList()
        val recentSet = recentHistory.toSet()

        // בתור החכם, ב"לא נלמדו" וב"קשות" - מילים שסומנו כ"נלמד" מסוננות החוצה לצמיתות!
        val filtered = when (filter) {
            StudyFilter.SMART -> allWords.filter { !it.isLearned }
            StudyFilter.UNLEARNED -> allWords.filter { !it.isLearned }
            StudyFilter.HARD -> allWords.filter { it.isHard && !it.isLearned }
            StudyFilter.ALL -> allWords
        }

        if (filtered.isEmpty()) return emptyList()

        return filtered.sortedByDescending { card ->
            if (filter == StudyFilter.ALL) {
                // בלשונית "הכל" מציגים קודם את אלו שלא נלמדו ואז את אלו שנלמדו
                (if (card.isLearned) 0.0 else 100.0) + Random.nextDouble(-10.0, 10.0)
            } else {
                card.calculatePriorityScore(stepCounter, recentSet.contains(card.english.lowercase()))
            }
        }
    }

    @Synchronized
    fun pickNextSmartWord(
        allWords: List<WordCard>,
        filter: StudyFilter,
        currentEnglish: String?
    ): WordCard? {
        if (!currentEnglish.isNullOrBlank()) {
            val key = currentEnglish.lowercase()
            recentHistory.remove(key)
            recentHistory.addLast(key)
            while (recentHistory.size > 3) {
                recentHistory.removeFirst()
            }
        }

        val queue = sortSmartQueue(allWords, filter)
        if (queue.isEmpty()) return null
        if (queue.size == 1) return queue.first()

        // בוחר את המילה בעלת העדיפות הגבוהה ביותר שאינה המילה הנוכחית על המסך
        return queue.firstOrNull { !it.english.equals(currentEnglish, ignoreCase = true) }
            ?: queue.first()
    }
}
