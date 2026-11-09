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
    val normalizedKey: String
        get() = english.trim().lowercase()
}

class WordRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_words_prefs_v2", Context.MODE_PRIVATE)

    // רשימה שחורה מיידית בזיכרון למילים שנלמדו - מונעת ב-100% חזרה שלהן
    private val learnedBlacklist = HashSet<String>()

    // מפת קירור קשיחה למילים שסומנו כקשות (שומרת באיזה צעד מותר להציג אותן שוב)
    private val hardCooldownMap = HashMap<String, Int>()

    // היסטוריית 8 המילים האחרונות שהוצגו כדי למנוע לופים חוזרים
    private val recentHistory = ArrayDeque<String>(10)

    private var stepCounter: Int = prefs.getInt("global_step_counter", 0)

    @Synchronized
    fun loadWords(): List<WordCard> {
        val wordList = mutableListOf<WordCard>()
        val seenKeys = HashSet<String>()
        learnedBlacklist.clear()
        hardCooldownMap.clear()

        try {
            val jsonString = context.assets.open("words.json").bufferedReader().use { reader ->
                reader.readText()
            }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val english = item.optString("english", "").trim()
                val hebrew = item.optString("hebrew", "").trim()
                val key = english.lowercase()

                if (english.isNotEmpty() && hebrew.isNotEmpty() && seenKeys.add(key)) {
                    val isLearned = prefs.getBoolean("${key}_learned", false)
                    val isHard = prefs.getBoolean("${key}_hard", false) && !isLearned
                    val dueStep = prefs.getInt("${key}_due_step", 0)

                    if (isLearned) {
                        learnedBlacklist.add(key)
                    }
                    if (isHard && dueStep > stepCounter) {
                        hardCooldownMap[key] = dueStep
                    }

                    wordList.add(
                        WordCard(
                            english = english,
                            hebrew = hebrew,
                            isLearned = isLearned,
                            isHard = isHard,
                            timesSeen = prefs.getInt("${key}_seen", 0),
                            hardCount = prefs.getInt("${key}_hard_count", 0),
                            scheduledAtStep = dueStep,
                            lastSeenTimestamp = prefs.getLong("${key}_last", 0L)
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
        val key = word.normalizedKey
        stepCounter++
        word.timesSeen++
        word.lastSeenTimestamp = System.currentTimeMillis()

        // הוספה מיידית להיסטוריה כדי שלא תוגרל שוב בצעד הבא
        recentHistory.remove(key)
        recentHistory.addLast(key)
        while (recentHistory.size > 8) {
            recentHistory.removeFirst()
        }

        when {
            learned == true -> {
                word.isLearned = true
                word.isHard = false
                word.scheduledAtStep = Int.MAX_VALUE
                learnedBlacklist.add(key)
                hardCooldownMap.remove(key)
            }
            hard == true -> {
                word.isHard = true
                word.isLearned = false
                word.hardCount++
                learnedBlacklist.remove(key)
                // מרחיק את המילה הקשה ל-6 עד 9 כרטיסים קדימה כדי שלא תחזור מיד בלופ!
                val cooldownSteps = Random.nextInt(6, 10)
                val nextAllowedStep = stepCounter + cooldownSteps
                word.scheduledAtStep = nextAllowedStep
                hardCooldownMap[key] = nextAllowedStep
            }
        }

        saveWordState(word)
    }

    @Synchronized
    fun saveWordState(word: WordCard) {
        val key = word.normalizedKey
        try {
            prefs.edit().apply {
                putInt("global_step_counter", stepCounter)
                putBoolean("${key}_learned", word.isLearned)
                putBoolean("${key}_hard", word.isHard)
                putInt("${key}_seen", word.timesSeen)
                putInt("${key}_hard_count", word.hardCount)
                putInt("${key}_due_step", word.scheduledAtStep)
                putLong("${key}_last", word.lastSeenTimestamp)
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
            learnedBlacklist.clear()
            hardCooldownMap.clear()
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
    fun pickNextSmartWord(
        allWords: List<WordCard>,
        filter: StudyFilter,
        excludedEnglish: String?
    ): WordCard? {
        if (allWords.isEmpty()) return null
        val excludedKey = excludedEnglish?.trim()?.lowercase()

        if (!excludedKey.isNullOrEmpty()) {
            recentHistory.remove(excludedKey)
            recentHistory.addLast(excludedKey)
            while (recentHistory.size > 8) {
                recentHistory.removeFirst()
            }
        }

        // שלב 1: סינון קשיח - מילים שנלמדו לעולם לא נכנסות לתור הלמידה
        val candidates = when (filter) {
            StudyFilter.SMART, StudyFilter.UNLEARNED -> allWords.filter { card ->
                !card.isLearned && !learnedBlacklist.contains(card.normalizedKey)
            }
            StudyFilter.HARD -> allWords.filter { card ->
                card.isHard && !card.isLearned && !learnedBlacklist.contains(card.normalizedKey)
            }
            StudyFilter.ALL -> allWords
        }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        // מסנן החוצה את המילה שכרגע היתה על המסך
        val withoutCurrent = candidates.filter { it.normalizedKey != excludedKey }
        val pool = withoutCurrent.ifEmpty { candidates }

        // מסנן החוצה מילים שנמצאות ב"קירור" (כאלו שסומנו כקשות ב-6 הצעדים האחרונים או נראו לאחרונה)
        val readyPool = pool.filter { card ->
            val key = card.normalizedKey
            val cooldownUntil = hardCooldownMap[key] ?: 0
            val notInHardCooldown = stepCounter >= cooldownUntil
            val notInRecentHistory = !recentHistory.contains(key)
            notInHardCooldown && notInRecentHistory
        }

        // אם יש מילים פנויות שלא בקירור - נבחר מתוכן. אם נשארו מעט מילים במאגר - נשחרר לפי הסדר הישן ביותר
        val finalSelectionPool = when {
            readyPool.isNotEmpty() -> readyPool
            else -> pool.sortedBy { card ->
                val historyIndex = recentHistory.indexOf(card.normalizedKey)
                val cooldownLeft = (hardCooldownMap[card.normalizedKey] ?: 0) - stepCounter
                (if (historyIndex == -1) -100 else historyIndex * 10) + cooldownLeft.coerceAtLeast(0)
            }
        }

        return finalSelectionPool.maxByOrNull { card ->
            val key = card.normalizedKey
            val dueStep = hardCooldownMap[key] ?: card.scheduledAtStep
            var score = 100.0

            if (card.isHard && stepCounter >= dueStep) {
                // מילה קשה שסיימה את תקופת ההמתנה שלה חוזרת לתרגול
                score += 220.0 + (card.hardCount * 10.0).coerceAtMost(50.0)
            } else if (card.timesSeen == 0) {
                // עדיפות גבוהה למילים חדשות שעוד לא נראו
                score += 90.0
            } else {
                score += (40.0 - card.timesSeen * 6.0).coerceAtLeast(5.0)
            }

            score + Random.nextDouble(-8.0, 8.0)
        }
    }
}
