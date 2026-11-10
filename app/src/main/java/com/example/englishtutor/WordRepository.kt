package com.example.englishtutor

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

enum class StudyFilter(val label: String) {
    SMART("תור חכם"),
    UNLEARNED("לא נלמדו"),
    HARD("קשות בלבד"),
    ALL("הכל")
}

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

    private val learnedBlacklist = mutableSetOf<String>()
    private val hardCooldownMap = mutableMapOf<String, Int>()
    private val recentHistory = mutableListOf<String>()
    private var stepCounter: Int = prefs.getInt("global_step_counter", 0)
    private var isFetchingAiBatch = false

    // מאגר גיבוי ענק של מילים ברמה גבוהה למקרה של ניתוק אינטרנט רגעי
    private val builtInExpansionPool = listOf(
        WordCard("negotiate", "לנהל משא ומתן", "B2", "עסקים"),
        WordCard("inevitable", "בלתי נמנע", "C1", "מתקדם"),
        WordCard("obstacle", "מכשול", "B1", "יומיום"),
        WordCard("accurate", "מדויק", "B1", "מדע"),
        WordCard("enthusiasm", "התלהבות", "B2", "רגשות"),
        WordCard("Breakthrough", "פריצת דרך", "B2", "טכנולוגיה"),
        WordCard("Comprehensive", "מקיף / יסודי", "C1", "אקדמיה"),
        WordCard("Reluctant", "מהסס / חסר רצון", "C1", "רגשות"),
        WordCard("Sustainable", "בר-קיימא", "B2", "סביבה"),
        WordCard("Ambiguous", "דו-משמעי / מעורפל", "C1", "תקשורת"),
        WordCard("Collaborate", "לשתף פעולה", "B2", "עסקים"),
        WordCard("Elaborate", "לפרט / להרחיב", "C1", "תקשורת"),
        WordCard("Genuine", "אמיתי / כן", "B2", "יומיום"),
        WordCard("Mandatory", "חובה", "B2", "חוק"),
        WordCard("Versatile", "רב-תכליתי / גמיש", "C1", "טכנולוגיה"),
        WordCard("Simultaneously", "בו-זמנית", "C1", "מתקדם"),
        WordCard("Accomplish", "להשיג / להשלים", "B1", "קריירה"),
        WordCard("Sufficient", "מספיק", "B2", "יומיום"),
        WordCard("Vulnerable", "פגיע / חשוף", "B2", "פסיכולוגיה"),
        WordCard("Tremendous", "עצום / אדיר", "B2", "תיאור"),
        WordCard("Postpone", "לדחות למועד אחר", "B1", "עסקים"),
        WordCard("Hypothesis", "השערה מדעית", "C1", "מדע"),
        WordCard("Consequence", "השלכה / תוצאה", "B2", "יומיום"),
        WordCard("Profound", "עמוק / מעמיק", "C1", "פילוסופיה"),
        WordCard("Resilient", "בעל חוסן / עמיד", "B2", "אישיות"),
        WordCard("crucial", "מכריע / קריטי", "B2", "עסקים"),
        WordCard("phenomenon", "תופעה", "B2", "מדע"),
        WordCard("integrity", "יושרה", "C1", "אישיות"),
        WordCard("distinguish", "להבחין / להבדיל", "B2", "חשיבה"),
        WordCard("perseverance", "התמדה ונחישות", "C1", "אישיות"),
        WordCard("fluctuate", "להשתנות / להתנדנד", "C1", "כלכלה"),
        WordCard("incentive", "תמריץ", "B2", "כלכלה")
    )

    @Synchronized
    fun loadWords(): List<WordCard> {
        val wordList = mutableListOf<WordCard>()
        val seenKeys = mutableSetOf<String>()
        learnedBlacklist.clear()
        hardCooldownMap.clear()

        // 1. טעינת המילים המובנות מ-words.json
        try {
            val jsonString = context.assets.open("words.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            parseWordsJsonArray(jsonArray, wordList, seenKeys)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. טעינת כל המילים שה-AI הוסיף אוטומטית ברקע בסשנים קודמים
        try {
            val aiSavedWordsJson = prefs.getString("ai_background_words_json", "[]") ?: "[]"
            val aiArray = JSONArray(aiSavedWordsJson)
            parseWordsJsonArray(aiArray, wordList, seenKeys)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (wordList.isEmpty()) {
            wordList.add(WordCard("Resilience", "חוסן נפשי / עמידות", "B2", "פסיכולוגיה"))
        }
        return wordList
    }

    private fun parseWordsJsonArray(
        jsonArray: JSONArray,
        targetList: MutableList<WordCard>,
        seenKeys: MutableSet<String>
    ) {
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            val english = item.optString("english", "").trim()
            val hebrew = item.optString("hebrew", "").trim()
            val cefr = item.optString("cefr", "B1").trim()
            val category = item.optString("category", "כללי").trim()
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

                targetList.add(
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
    }

    // --- מנוע AI שקט ברקע שמוסיף מילים ושאלות חדשות למבחן האיות ולאתגר 60 השניות מבלי שיראו! ---
    suspend fun fetchInfiniteAiWordsSilently(existingWords: List<WordCard>): List<WordCard> = withContext(Dispatchers.IO) {
        if (isFetchingAiBatch) return@withContext emptyList()
        isFetchingAiBatch = true

        try {
            val existingSet = existingWords.map { it.normalizedKey }.toHashSet()
            val sampleExisting = existingWords.shuffled().take(15).joinToString(", ") { it.english }

            val onlineWords = runCatching {
                val url = URL("https://text.pollinations.ai/openai")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    connectTimeout = 10000
                    readTimeout = 16000
                    doOutput = true
                }

                val systemPrompt = """
                    Return ONLY a valid JSON array of 8 useful English vocabulary words for Hebrew speakers.
                    No markdown, no explanations.
                    Each object must have: "english" (single pure English word), "hebrew" (concise accurate Hebrew translation), "cefr" ("A2", "B1", "B2", or "C1"), "category" (Hebrew category).
                    Do not include these words: $sampleExisting.
                """.trimIndent()

                val userPrompt = """
                    Generate 8 fresh English words for spelling and speed quiz:
                    [{"english":"...", "hebrew":"...", "cefr":"B2", "category":"כללי"}]
                """.trimIndent()

                val messages = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userPrompt))
                }
                val body = JSONObject().apply {
                    put("model", "openai-fast")
                    put("messages", messages)
                    put("temperature", 0.85)
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
                val rawResponse = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }

                val content = if (rawResponse.trim().startsWith("{") && rawResponse.contains("\"choices\"")) {
                    JSONObject(rawResponse).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                } else {
                    rawResponse
                }

                val cleaned = content.replace("```json", "").replace("```", "").trim()
                val firstBracket = cleaned.indexOf('[')
                val lastBracket = cleaned.lastIndexOf(']')
                if (firstBracket == -1 || lastBracket <= firstBracket) throw IllegalStateException("No JSON array")

                val arr = JSONArray(cleaned.substring(firstBracket, lastBracket + 1))
                val parsed = mutableListOf<WordCard>()

                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val en = o.optString("english").replace(Regex("[^a-zA-Z\\-]"), "").trim()
                    val he = o.optString("hebrew").trim()
                    val cefr = o.optString("cefr", "B1").trim()
                    val cat = o.optString("category", "AI").trim()

                    if (en.length >= 3 && he.isNotEmpty() && !existingSet.contains(en.lowercase())) {
                        existingSet.add(en.lowercase())
                        parsed.add(WordCard(english = en, hebrew = he, cefr = cefr, category = cat))
                    }
                }
                parsed
            }.getOrDefault(emptyList())

            val finalNewWords = if (onlineWords.isNotEmpty()) {
                onlineWords
            } else {
                // גיבוי שקט מהמאגר הפנימי אם אין אינטרנט באותו רגע
                builtInExpansionPool.filter { !existingSet.contains(it.normalizedKey) }.shuffled().take(5)
            }

            if (finalNewWords.isNotEmpty()) {
                saveNewAiWordsToPrefs(finalNewWords)
            }
            return@withContext finalNewWords
        } finally {
            isFetchingAiBatch = false
        }
    }

    @Synchronized
    private fun saveNewAiWordsToPrefs(newWords: List<WordCard>) {
        try {
            val existingStr = prefs.getString("ai_background_words_json", "[]") ?: "[]"
            val arr = runCatching { JSONArray(existingStr) }.getOrDefault(JSONArray())
            for (w in newWords) {
                arr.put(
                    JSONObject().apply {
                        put("english", w.english)
                        put("hebrew", w.hebrew)
                        put("cefr", w.cefr)
                        put("category", w.category)
                    }
                )
            }
            prefs.edit().putString("ai_background_words_json", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun recordReview(word: WordCard, learned: Boolean? = null, hard: Boolean? = null) {
        val key = word.normalizedKey
        stepCounter++
        word.timesSeen++
        word.lastSeenTimestamp = System.currentTimeMillis()

        recentHistory.remove(key)
        recentHistory.add(key)
        while (recentHistory.size > 8) {
            recentHistory.removeAt(0)
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

        val levelFiltered = if (selectedCefr == CefrLevel.ALL) {
            allWords
        } else {
            allWords.filter { it.cefr.equals(selectedCefr.name, ignoreCase = true) }
        }

        val pool = levelFiltered.ifEmpty { allWords }
        val excludedKey = excludedEnglish?.trim()?.lowercase()

        val candidates = when (filter) {
            StudyFilter.SMART, StudyFilter.UNLEARNED -> pool.filter { card ->
                !card.isLearned && !learnedBlacklist.contains(card.normalizedKey)
            }
            StudyFilter.HARD -> pool.filter { card ->
                card.isHard && !card.isLearned && !learnedBlacklist.contains(card.normalizedKey)
            }
            StudyFilter.ALL -> pool
        }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        val withoutCurrent = candidates.filter { it.normalizedKey != excludedKey }.ifEmpty { candidates }

        val readyPool = withoutCurrent.filter { card ->
            val key = card.normalizedKey
            val cooldownUntil = hardCooldownMap[key] ?: 0
            stepCounter >= cooldownUntil && !recentHistory.contains(key)
        }

        val finalPool = readyPool.ifEmpty { withoutCurrent }

        return finalPool.maxByOrNull { card ->
            val key = card.normalizedKey
            var score = 100.0
            if (card.isHard && stepCounter >= (hardCooldownMap[key] ?: 0)) {
                score += 220.0
            }
            if (card.timesSeen == 0) {
                score += 85.0
            }
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
