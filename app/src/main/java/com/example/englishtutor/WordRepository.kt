package com.example.englishtutor

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import kotlin.random.Random

enum class CefrLevel(val label: String) {
    ALL("כל הרמות"),
    A1("A1 - מתחילים"),
    A2("A2 - בסיסי"),
    B1("B1 - בינוני"),
    B2("B2 - מתקדם"),
    C1("C1 - שולט"),
    C2("C2 - מומחה")
}

data class WordCard(
    val english: String,
    val hebrew: String,
    val cefr: String = "A1",
    val category: String = "יומיום",
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
        context.getSharedPreferences("smart_words_prefs_v3", Context.MODE_PRIVATE)

    private val learnedBlacklist = HashSet<String>()
    private val hardCooldownMap = HashMap<String, Int>()
    private val recentHistory = ArrayDeque<String>(10)
    private var stepCounter: Int = prefs.getInt("global_step_counter", 0)

    @Synchronized
    fun loadWords(): List<WordCard> {
        val wordList = mutableListOf<WordCard>()
        val seenKeys = HashSet<String>()
        learnedBlacklist.clear()
        hardCooldownMap.clear()

        try {
            val jsonString = context.assets.open("words.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val english = item.optString("english", "").trim()
                val hebrew = item.optString("hebrew", "").trim()
                val cefr = item.optString("cefr", "A1").trim()
                val category = item.optString("category", "כללי").trim()
                val key = english.lowercase()

                if (english.isNotEmpty() && hebrew.isNotEmpty() && seenKeys.add(key)) {
                    val isLearned = prefs.getBoolean("${key}_learned", false)
                    val isHard = prefs.getBoolean("${key}_hard", false) && !isLearned
                    val dueStep = prefs.getInt("${key}_due_step", 0)

                    if (isLearned) learnedBlacklist.add(key)
                    if (isHard && dueStep > stepCounter) hardCooldownMap[key] = dueStep

                    wordList.add(
                        WordCard(
                            english = english,
                            hebrew = hebrew,
                            cefr = cefr,
                            category = category,
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
            wordList.add(WordCard("Resilience", "חוסן נפשי / עמידות", "B2", "פסיכולוגיה"))
        }
        return wordList
    }

    @Synchronized
    fun recordReview(word: WordCard, learned: Boolean? = null, hard: Boolean? = null) {
        val key = word.normalizedKey
        stepCounter++
        word.timesSeen++
        word.lastSeenTimestamp = System.currentTimeMillis()

        recentHistory.remove(key)
        recentHistory.addLast(key)
        while (recentHistory.size > 8) recentHistory.removeFirst()

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
        excludedEnglish: String?,
        selectedCefr: CefrLevel = CefrLevel.ALL
    ): WordCard? {
        if (allWords.isEmpty()) return null

        val levelFiltered = if (selectedCefr == CefrLevel.ALL) allWords
        else allWords.filter { it.cefr.equals(selectedCefr.name, ignoreCase = true) }

        val pool = levelFiltered.ifEmpty { allWords }
        val excludedKey = excludedEnglish?.trim()?.lowercase()

        val candidates = when (filter) {
            StudyFilter.SMART, StudyFilter.UNLEARNED -> pool.filter { !it.isLearned && !learnedBlacklist.contains(it.normalizedKey) }
            StudyFilter.HARD -> pool.filter { it.isHard && !it.isLearned && !learnedBlacklist.contains(it.normalizedKey) }
            StudyFilter.ALL -> pool
        }

        if (candidates.isEmpty()) return null
        val withoutCurrent = candidates.filter { it.normalizedKey != excludedKey }.ifEmpty { candidates }

        return withoutCurrent.maxByOrNull { card ->
            val key = card.normalizedKey
            var score = 100.0
            if (card.isHard && stepCounter >= (hardCooldownMap[key] ?: 0)) score += 200.0
            if (card.timesSeen == 0) score += 80.0
            score + Random.nextDouble(-10.0, 10.0)
        }
    }

    fun getSpeedHighScore(): Int = prefs.getInt("speed_challenge_high_score", 0)

    fun saveSpeedHighScore(score: Int) {
        val current = getSpeedHighScore()
        if (score > current) {
            prefs.edit().putInt("speed_challenge_high_score", score).apply()
        }
    }
}
