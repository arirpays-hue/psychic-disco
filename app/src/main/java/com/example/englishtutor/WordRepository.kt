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
import java.net.URLEncoder
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
    private val speedAttackRecentHistory = ArrayDeque<String>(60)
    private var stepCounter: Int = prefs.getInt("global_step_counter", 0)
    private var isFetchingAiBatch = false

    // מסווג אוטומטית מילים ללא רמה מוגדרת ל-A1 עד C2 לפי אורך ומורכבות כדי שכל שלב בעץ יהיה מלא במילים!
    private fun inferCefrLevelForWord(english: String, explicitCefr: String, index: Int): String {
        if (explicitCefr.isNotBlank() && explicitCefr != "B1") return explicitCefr.uppercase()
        val len = english.length
        return when {
            len <= 4 -> "A1"
            len == 5 -> "A2"
            len in 6..7 -> "B1"
            len in 8..9 -> "B2"
            len == 10 -> "C1"
            len >= 11 -> "C2"
            else -> listOf("A1", "A2", "B1", "B2", "C1", "C2")[index % 6]
        }
    }

    @Synchronized
    fun loadWords(): List<WordCard> {
        val wordList = mutableListOf<WordCard>()
        val seenKeys = mutableSetOf<String>()
        learnedBlacklist.clear()
        hardCooldownMap.clear()

        try {
            val savedCustomJson = prefs.getString("custom_deck_words_json", "[]") ?: "[]"
            parseWordsJsonArray(JSONArray(savedCustomJson), wordList, seenKeys)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val jsonString = context.assets.open("words.json").bufferedReader().use { it.readText() }
            parseWordsJsonArray(JSONArray(jsonString), wordList, seenKeys)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val aiSavedWordsJson = prefs.getString("ai_background_words_json", "[]") ?: "[]"
            parseWordsJsonArray(JSONArray(aiSavedWordsJson), wordList, seenKeys)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (wordList.isEmpty()) {
            wordList.add(WordCard("Start", "התחלה", "A1", "יסודות"))
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
            val rawCefr = item.optString("cefr", "").trim()
            val cefr = inferCefrLevelForWord(english, rawCefr, i)
            val category = item.optString("category", "כללי").trim()
            val key = english.lowercase()

            if (english.isNotEmpty() && hebrew.isNotEmpty() && seenKeys.add(key)) {
                val isLearned = prefs.getBoolean("${key}_learned", false)
                val isHard = prefs.getBoolean("${key}_hard", false) && !isLearned
                val dueStep = prefs.getInt("${key}_due_step", 0)

                if (isLearned) learnedBlacklist.add(key)
                if (isHard && dueStep > stepCounter) hardCooldownMap[key] = dueStep

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

    private fun translateQuick(query: String, fromLang: String, toLang: String): String {
        return runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$fromLang&tl=$toLang&dt=t&q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 5000
                readTimeout = 5000
            }
            val res = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            JSONArray(res).getJSONArray(0).getJSONArray(0).getString(0).trim()
        }.getOrDefault("")
    }

    suspend fun generateCustomAiDeck(topic: String, count: Int = 10): List<WordCard> = withContext(Dispatchers.IO) {
        val cleanTopic = topic.trim().ifEmpty { "אנגלית שימושית" }

        val onlineCards = runCatching {
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
                Return ONLY a valid JSON array of $count English vocabulary words with accurate Hebrew translations about: "$cleanTopic".
                Format: [{"english": "Word", "hebrew": "תרגום לעברית", "cefr": "A1", "category": "$cleanTopic"}]
            """.trimIndent()

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", "Create $count English vocabulary words about $cleanTopic"))
            }

            val body = JSONObject().apply {
                put("model", "openai-fast")
                put("messages", messages)
                put("temperature", 0.75)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val raw = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }

            val content = if (raw.trim().startsWith("{") && raw.contains("\"choices\"")) {
                JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } else raw

            val start = content.indexOf('[')
            val end = content.lastIndexOf(']')
            if (start == -1 || end <= start) throw IllegalStateException("No JSON array")
            val arr = JSONArray(content.substring(start, end + 1))

            val parsed = mutableListOf<WordCard>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val en = o.optString("english").replace(Regex("[^a-zA-Z\\s-]"), "").trim()
                var he = o.optString("hebrew").trim()
                if (en.length >= 2 && he.isEmpty()) {
                    he = translateQuick(en, "en", "iw")
                }
                val cefr = o.optString("cefr", "A1").trim()
                if (en.length >= 2 && he.isNotEmpty()) {
                    parsed.add(WordCard(english = en, hebrew = he, cefr = cefr, category = cleanTopic))
                }
            }
            parsed
        }.getOrDefault(emptyList())

        val finalCards = if (onlineCards.isNotEmpty()) {
            onlineCards
        } else {
            val translatedTopicEn = translateQuick(cleanTopic, "iw", "en")
                .replace(Regex("[^a-zA-Z\\s-]"), "")
                .trim()
            val list = mutableListOf<WordCard>()
            if (translatedTopicEn.length >= 2) {
                list.add(WordCard(translatedTopicEn.replaceFirstChar { it.uppercase() }, cleanTopic, "A1", cleanTopic))
            }
            list.addAll(
                listOf(
                    WordCard("Architecture", "ארכיטקטורת מערכת", "A1", cleanTopic),
                    WordCard("Deployment", "פריסה / העלאה לאוויר", "A1", cleanTopic),
                    WordCard("Opportunity", "הזדמנות", "A1", cleanTopic),
                    WordCard("Strategy", "אסטרטגיה", "A1", cleanTopic)
                )
            )
            list.distinctBy { it.normalizedKey }
        }

        if (finalCards.isNotEmpty()) {
            val existingCustom = prefs.getString("custom_deck_words_json", "[]") ?: "[]"
            val customArray = runCatching { JSONArray(existingCustom) }.getOrDefault(JSONArray())
            val updatedArray = JSONArray()

            for (card in finalCards) {
                updatedArray.put(JSONObject().apply {
                    put("english", card.english)
                    put("hebrew", card.hebrew)
                    put("cefr", card.cefr)
                    put("category", card.category)
                })
            }
            for (i in 0 until customArray.length()) {
                customArray.optJSONObject(i)?.let { updatedArray.put(it) }
            }
            prefs.edit().putString("custom_deck_words_json", updatedArray.toString()).apply()
        }

        return@withContext finalCards
    }

    fun exportProgressToJson(words: List<WordCard>): String {
        val root = JSONObject()
        val arr = JSONArray()
        for (w in words) {
            arr.put(JSONObject().apply {
                put("english", w.english)
                put("hebrew", w.hebrew)
                put("cefr", w.cefr)
                put("category", w.category)
                put("isLearned", w.isLearned)
                put("isHard", w.isHard)
            })
        }
        root.put("timestamp", System.currentTimeMillis())
        root.put("words", arr)
        return root.toString(2)
    }

    fun importWordsFromCsv(csvText: String): Int {
        var count = 0
        val existing = prefs.getString("custom_deck_words_json", "[]") ?: "[]"
        val customArray = runCatching { JSONArray(existing) }.getOrDefault(JSONArray())

        csvText.lines().forEach { line ->
            val parts = line.split(",", "\t", ";")
            if (parts.size >= 2) {
                val en = parts[0].trim().replace("\"", "")
                val he = parts[1].trim().replace("\"", "")
                if (en.length >= 2 && he.length >= 2) {
                    customArray.put(JSONObject().apply {
                        put("english", en)
                        put("hebrew", he)
                        put("cefr", "A1")
                        put("category", "ייבוא אישי")
                    })
                    count++
                }
            }
        }
        prefs.edit().putString("custom_deck_words_json", customArray.toString()).apply()
        return count
    }

    @Synchronized
    fun pickRandomNonRepeatingWord(
        allWords: List<WordCard>,
        sessionAskedKeys: Set<String>
    ): WordCard {
        val randomSeed = Random(System.nanoTime())
        val freshCandidates = allWords.filter { card ->
            val key = card.normalizedKey
            !sessionAskedKeys.contains(key) && !speedAttackRecentHistory.contains(key)
        }

        val chosen = when {
            freshCandidates.isNotEmpty() -> freshCandidates.random(randomSeed)
            else -> {
                speedAttackRecentHistory.clear()
                val notInThisSession = allWords.filter { !sessionAskedKeys.contains(it.normalizedKey) }
                if (notInThisSession.isNotEmpty()) notInThisSession.random(randomSeed)
                else allWords.random(randomSeed)
            }
        }

        val chosenKey = chosen.normalizedKey
        speedAttackRecentHistory.remove(chosenKey)
        speedAttackRecentHistory.addLast(chosenKey)
        while (speedAttackRecentHistory.size > 55) {
            speedAttackRecentHistory.removeFirst()
        }
        return chosen
    }

    suspend fun fetchInfiniteAiWordsSilently(existingWords: List<WordCard>): List<WordCard> = withContext(Dispatchers.IO) {
        if (isFetchingAiBatch) return@withContext emptyList()
        isFetchingAiBatch = true
        try {
            val existingSet = existingWords.map { it.normalizedKey }.toHashSet()
            val sampleExisting = existingWords.shuffled(Random(System.nanoTime())).take(15).joinToString(", ") { it.english }

            val onlineWords = runCatching {
                val url = URL("https://text.pollinations.ai/openai")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    connectTimeout = 8000
                    readTimeout = 14000
                    doOutput = true
                }

                val systemPrompt = "Return ONLY a raw JSON array of 8 vocabulary objects: [{\"english\":\"...\",\"hebrew\":\"...\",\"cefr\":\"B1\",\"category\":\"כללי\"}] avoiding: $sampleExisting"
                val messages = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                }
                val body = JSONObject().apply {
                    put("model", "openai-fast")
                    put("messages", messages)
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
                val raw = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val start = raw.indexOf('[')
                val end = raw.lastIndexOf(']')
                val arr = JSONArray(raw.substring(start, end + 1))
                val parsed = mutableListOf<WordCard>()

                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val en = o.optString("english").replace(Regex("[^a-zA-Z\\-]"), "").trim()
                    val he = o.optString("hebrew").trim()
                    if (en.length >= 3 && he.isNotEmpty() && !existingSet.contains(en.lowercase())) {
                        existingSet.add(en.lowercase())
                        parsed.add(WordCard(english = en, hebrew = he, cefr = o.optString("cefr", "B1"), category = "AI"))
                    }
                }
                parsed
            }.getOrDefault(emptyList())

            return@withContext onlineWords
        } finally {
            isFetchingAiBatch = false
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
            if (card.isHard && stepCounter >= (hardCooldownMap[key] ?: 0)) score += 220.0
            if (card.timesSeen == 0) score += 85.0
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
