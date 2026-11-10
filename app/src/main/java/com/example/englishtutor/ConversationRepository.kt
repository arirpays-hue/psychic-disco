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

data class DialogueOption(
    val textEn: String,
    val textHe: String,
    val nextNodeId: String,
    val feedbackHe: String,
    val isOptimal: Boolean = true
)

data class DialogueNode(
    val id: String,
    val speakerName: String,
    val speakerLineEn: String,
    val speakerLineHe: String,
    val options: List<DialogueOption>,
    val isEnding: Boolean = false
)

data class ConversationScenario(
    val id: String,
    val categoryHe: String,
    val titleHe: String,
    val titleEn: String,
    val difficultyHe: String,
    val nodes: Map<String, DialogueNode>,
    val isAiGenerated: Boolean = false
)

// מודל מבחן אמריקאי / אינטראקציה חיה שנוצרת בתוך הצ'אט החופשי
data class InteractiveChatQuiz(
    val typeTitleHe: String,
    val questionEn: String,
    val questionHe: String,
    val optionsEn: List<String>,
    val correctIndex: Int,
    val explanationHe: String
)

data class FreeChatResponse(
    val replyEn: String,
    val correctionHe: String?,
    val interactiveQuiz: InteractiveChatQuiz? = null
)

class ConversationRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("conversations_prefs_v3", Context.MODE_PRIVATE)

    fun getCustomApiKey(): String = prefs.getString("custom_ai_api_key", "") ?: ""

    fun saveCustomApiKey(key: String) {
        prefs.edit().putString("custom_ai_api_key", key.trim()).apply()
    }

    fun loadScenarios(): List<ConversationScenario> {
        val list = mutableListOf<ConversationScenario>()

        val savedAiJson = prefs.getString("ai_generated_scenarios_json", "[]") ?: "[]"
        list.addAll(parseScenariosArray(savedAiJson, isAi = true))

        try {
            val assetsJson = context.assets.open("conversations.json").bufferedReader().use { it.readText() }
            list.addAll(parseScenariosArray(assetsJson, isAi = false))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }

    private fun sanitizeEnglishOnly(text: String, fallback: String): String {
        val withoutHebrew = text.replace(Regex("[\\u0590-\\u05FF]+"), "").replace("()", "").replace("  ", " ").trim()
        return if (withoutHebrew.length >= 2) withoutHebrew else fallback
    }

    // שליחת טקסט ישירות לשרת התרגום החינמי של גוגל (עבור לחיצה ארוכה על הודעה בצ'אט)
    suspend fun translateTextOnline(englishText: String): String = withContext(Dispatchers.IO) {
        translateEnToHeFallback(englishText)
    }

    private fun translateEnToHeFallback(englishText: String): String {
        if (englishText.isBlank()) return ""
        return runCatching {
            val encoded = URLEncoder.encode(englishText.trim(), "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=iw&dt=t&q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 6000
                readTimeout = 6000
            }
            val response = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val outerArray = JSONArray(response)
            val sentences = outerArray.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until sentences.length()) {
                val part = sentences.optJSONArray(i)
                if (part != null && part.length() > 0) {
                    sb.append(part.optString(0, ""))
                }
            }
            sb.toString().trim()
        }.getOrDefault("לא ניתן היה להתחבר לשרת התרגום כרגע. בדוק חיבור אינטרנט.")
    }

    private fun parseScenariosArray(jsonStr: String, isAi: Boolean): List<ConversationScenario> {
        val result = mutableListOf<ConversationScenario>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                parseSingleScenario(obj, isAi)?.let { result.add(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun parseSingleScenario(obj: JSONObject, isAiFallback: Boolean): ConversationScenario? {
        val id = obj.optString("id").trim()
        val categoryHe = obj.optString("categoryHe", if (isAiFallback) "שיחות AI" else "כללי").trim()
        val titleHe = obj.optString("titleHe").trim()
        val titleEn = obj.optString("titleEn", "English Roleplay Conversation").trim()
        val difficultyHe = obj.optString("difficultyHe", "בינוני").trim()
        val isAi = obj.optBoolean("isAiGenerated", isAiFallback)

        val nodesMap = LinkedHashMap<String, DialogueNode>()
        val nodesArr = obj.optJSONArray("nodes") ?: JSONArray()

        for (j in 0 until nodesArr.length()) {
            val nObj = nodesArr.optJSONObject(j) ?: continue
            val nodeId = nObj.optString("id").trim()
            val speakerName = nObj.optString("speakerName", "Speaker").trim()
            val rawSpeakerEn = nObj.optString("speakerLineEn").trim()
            val speakerLineEn = sanitizeEnglishOnly(rawSpeakerEn, "Hello! Could you please explain the details so I can assist you?")

            val speakerLineHe = nObj.optString("speakerLineHe").ifBlank {
                nObj.optString("hebrew").ifBlank { nObj.optString("translation") }
            }.trim()

            val isEnding = nObj.optBoolean("isEnding", false)

            val optionsList = mutableListOf<DialogueOption>()
            val optsArr = nObj.optJSONArray("options") ?: JSONArray()
            for (k in 0 until optsArr.length()) {
                val oObj = optsArr.optJSONObject(k) ?: continue
                val rawOptEn = oObj.optString("textEn").trim()
                val textEn = sanitizeEnglishOnly(rawOptEn, "")
                val textHe = oObj.optString("textHe").ifBlank {
                    oObj.optString("hebrew").ifBlank { oObj.optString("translation") }
                }.trim()

                if (textEn.isNotEmpty()) {
                    optionsList.add(
                        DialogueOption(
                            textEn = textEn,
                            textHe = textHe,
                            nextNodeId = oObj.optString("nextNodeId", nodeId).trim(),
                            feedbackHe = oObj.optString("feedbackHe").trim(),
                            isOptimal = oObj.optBoolean("isOptimal", false)
                        )
                    )
                }
            }

            if (nodeId.isNotEmpty() && speakerLineEn.isNotEmpty()) {
                nodesMap[nodeId] = DialogueNode(
                    id = nodeId,
                    speakerName = speakerName,
                    speakerLineEn = speakerLineEn,
                    speakerLineHe = speakerLineHe,
                    options = optionsList,
                    isEnding = isEnding
                )
            }
        }

        return if (id.isNotEmpty() && titleHe.isNotEmpty() && nodesMap.isNotEmpty()) {
            ConversationScenario(
                id = id,
                categoryHe = categoryHe,
                titleHe = titleHe,
                titleEn = titleEn,
                difficultyHe = difficultyHe,
                nodes = nodesMap,
                isAiGenerated = isAi
            )
        } else null
    }

    private fun enrichMissingHebrewTranslations(scenarioObj: JSONObject) {
        val nodesArr = scenarioObj.optJSONArray("nodes") ?: return
        for (i in 0 until nodesArr.length()) {
            val nObj = nodesArr.optJSONObject(i) ?: continue
            val cleanEn = sanitizeEnglishOnly(
                nObj.optString("speakerLineEn"),
                "Hello! Could you please share the details so I can help you today?"
            )
            nObj.put("speakerLineEn", cleanEn)

            val currentHe = nObj.optString("speakerLineHe").trim()
            if (currentHe.isEmpty()) {
                nObj.put("speakerLineHe", translateEnToHeFallback(cleanEn))
            }

            val optsArr = nObj.optJSONArray("options") ?: continue
            for (j in 0 until optsArr.length()) {
                val oObj = optsArr.optJSONObject(j) ?: continue
                val optEn = sanitizeEnglishOnly(oObj.optString("textEn"), "Yes, let me explain.")
                oObj.put("textEn", optEn)
                if (oObj.optString("textHe").isBlank()) {
                    oObj.put("textHe", translateEnToHeFallback(optEn))
                }
                if (oObj.optString("feedbackHe").isBlank()) {
                    val isOpt = oObj.optBoolean("isOptimal", false)
                    oObj.put(
                        "feedbackHe",
                        if (isOpt) "תשובה מצוינת! דקדוק תקין וניסוח מנומס."
                        else "תשובה שגויה דקדוקית או שאינה מתאימה להקשר השיחה. נסה שוב!"
                    )
                }
            }
        }
    }

    private fun saveAiScenarioToPrefs(scenarioObj: JSONObject) {
        try {
            scenarioObj.put("isAiGenerated", true)
            val existingStr = prefs.getString("ai_generated_scenarios_json", "[]") ?: "[]"
            val existingArr = runCatching { JSONArray(existingStr) }.getOrDefault(JSONArray())
            val updatedArr = JSONArray()
            updatedArr.put(scenarioObj)
            for (i in 0 until existingArr.length().coerceAtMost(30)) {
                existingArr.optJSONObject(i)?.let { updatedArr.put(it) }
            }
            prefs.edit().putString("ai_generated_scenarios_json", updatedArr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isScenarioCompleted(scenarioId: String): Boolean =
        prefs.getBoolean("completed_$scenarioId", false)

    fun markScenarioCompleted(scenarioId: String) {
        prefs.edit().putBoolean("completed_$scenarioId", true).apply()
    }

    private suspend fun requestAiJson(systemPrompt: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        val customKey = getCustomApiKey()

        if (customKey.startsWith("AIza")) {
            val geminiUrl = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$customKey")
            val conn = (geminiUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "EnglishTutorAndroid/5.0")
                connectTimeout = 20000
                readTimeout = 35000
                doOutput = true
            }
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", "$systemPrompt\n\n$userPrompt")
                    ))
                ))
                put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val root = JSONObject(responseText)
            return@withContext root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }

        val endpointUrl = if (customKey.startsWith("sk-")) {
            URL("https://openrouter.ai/api/v1/chat/completions")
        } else {
            URL("https://text.pollinations.ai/openai")
        }

        val conn = (endpointUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            if (customKey.startsWith("sk-")) {
                setRequestProperty("Authorization", "Bearer $customKey")
            }
            connectTimeout = 14000
            readTimeout = 24000
            doOutput = true
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val body = JSONObject().apply {
            put("model", if (customKey.startsWith("sk-")) "google/gemini-2.5-flash" else "openai-fast")
            put("messages", messages)
            put("temperature", 0.7)
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
        val rawResponse = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        val trimmed = rawResponse.trim()
        if (trimmed.startsWith("{") && trimmed.contains("\"choices\"")) {
            val root = JSONObject(trimmed)
            return@withContext root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
        return@withContext trimmed
    }

    private fun extractCleanJsonObject(raw: String): JSONObject {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return JSONObject(cleaned.substring(firstBrace, lastBrace + 1))
        }
        throw IllegalArgumentException("Invalid JSON format")
    }

    private fun extractCleanJsonArray(raw: String): JSONArray {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        val firstBracket = cleaned.indexOf('[')
        val lastBracket = cleaned.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket > firstBracket) {
            return JSONArray(cleaned.substring(firstBracket, lastBracket + 1))
        }
        throw IllegalArgumentException("Invalid JSON Array format")
    }

    // --- מענה בצ'אט החופשי כולל זיהוי אוטומטי של בקשות למבחן אמריקאי / השלמת משפטים / תרגול ---
    suspend fun generateFreeChatReply(userMessage: String, history: List<ChatMessage>): FreeChatResponse = withContext(Dispatchers.IO) {
        val lower = userMessage.lowercase()
        val wantsQuiz = lower.contains("quiz") || lower.contains("test") ||
            userMessage.contains("מבחן") || userMessage.contains("חידון") ||
            userMessage.contains("אמריקאי") || userMessage.contains("שאלה")
        val wantsFillBlank = lower.contains("fill") || lower.contains("blank") ||
            userMessage.contains("השלמ") || userMessage.contains("משפט חסר")
        val wantsVocabChallenge = lower.contains("challenge") || lower.contains("vocab") ||
            userMessage.contains("אתגר") || userMessage.contains("אוצר מילים")

        if (wantsQuiz || wantsFillBlank || wantsVocabChallenge) {
            val modeType = when {
                wantsFillBlank -> "FILL_BLANK"
                wantsVocabChallenge -> "VOCAB_CHALLENGE"
                else -> "MULTIPLE_CHOICE"
            }
            return@withContext generateInteractiveChatChallenge(modeType, userMessage)
        }

        runCatching {
            val systemPrompt = """
                You are a friendly, intelligent English tutor for Hebrew speakers.
                Respond in natural, engaging English (1-2 sentences).
                Analyze the user's message for grammar, phrasing, or spelling mistakes:
                - If there is a mistake, explain it constructively in Hebrew in "correctionHe".
                - If the user's English is correct or if the user wrote in Hebrew, set "correctionHe" to null.
                Format output strictly as JSON: {"replyEn": "...", "correctionHe": "..."}
            """.trimIndent()

            val jsonStr = requestAiJson(systemPrompt, userMessage)
            val obj = extractCleanJsonObject(jsonStr)
            val replyEn = sanitizeEnglishOnly(
                obj.optString("replyEn"),
                "That sounds very interesting! Could you tell me more about your perspective on that?"
            )
            val correctionHe = if (obj.has("correctionHe") && !obj.isNull("correctionHe") && obj.getString("correctionHe").isNotBlank()) {
                obj.getString("correctionHe")
            } else null

            FreeChatResponse(replyEn, correctionHe, null)
        }.getOrElse {
            FreeChatResponse(
                replyEn = "I'd love to hear more about that! How would you describe it in your own words?",
                correctionHe = null,
                interactiveQuiz = null
            )
        }
    }

    // --- מחולל מבחנים אמריקאיים ואינטראקציות חיות בזמן אמת בתוך הצ'אט ---
    suspend fun generateInteractiveChatChallenge(
        interactionType: String,
        customTopic: String = ""
    ): FreeChatResponse = withContext(Dispatchers.IO) {
        val typeTitleHe = when (interactionType) {
            "FILL_BLANK" -> "אתגר השלמת משפט (Fill in the Blank)"
            "VOCAB_CHALLENGE" -> "אתגר אוצר מילים וניבים (Idiom & Vocab)"
            "GRAMMAR_FIX" -> "אתגר זיהוי המשפט התקין דקדוקית"
            else -> "מבחן אמריקאי חי (Multiple Choice Quiz)"
        }

        val onlineQuiz = runCatching {
            val systemPrompt = """
                You are an interactive English exam generator for Hebrew speakers.
                Return ONLY a valid JSON object creating a 4-option interactive English question.
                Interaction Type: $interactionType.
                Rules:
                - "replyEn": Short encouraging intro in pure English.
                - "questionEn": The question or fill-in-the-blank sentence in pure English.
                - "questionHe": Full Hebrew translation of the question.
                - "optionsEn": JSON array of 4 pure English options.
                - "correctIndex": Integer 0, 1, 2, or 3 indicating the correct option.
                - "explanationHe": Detailed Hebrew explanation of why the correct option is right and others are wrong.
            """.trimIndent()

            val userPrompt = """
                Topic/Request: "${customTopic.ifBlank { "General practical English, grammar & travel/work idioms" }}"
                Required JSON format:
                {
                  "replyEn": "Here is an interactive challenge for you! Tap the correct answer below:",
                  "questionEn": "...",
                  "questionHe": "...",
                  "optionsEn": ["Option A", "Option B", "Option C", "Option D"],
                  "correctIndex": 0,
                  "explanationHe": "..."
                }
            """.trimIndent()

            val raw = requestAiJson(systemPrompt, userPrompt)
            val obj = extractCleanJsonObject(raw)
            val optsArr = obj.getJSONArray("optionsEn")
            val optsList = mutableListOf<String>()
            for (i in 0 until optsArr.length()) {
                optsList.add(sanitizeEnglishOnly(optsArr.optString(i), "Option ${i + 1}"))
            }
            while (optsList.size < 4) {
                optsList.add("None of the above")
            }

            val qEn = sanitizeEnglishOnly(obj.optString("questionEn"), "Which sentence is grammatically correct?")
            val qHe = obj.optString("questionHe").ifBlank { translateEnToHeFallback(qEn) }
            val corrIdx = obj.optInt("correctIndex", 0).coerceIn(0, optsList.size - 1)

            FreeChatResponse(
                replyEn = sanitizeEnglishOnly(obj.optString("replyEn"), "Here is your interactive challenge! Select the right answer:"),
                correctionHe = null,
                interactiveQuiz = InteractiveChatQuiz(
                    typeTitleHe = typeTitleHe,
                    questionEn = qEn,
                    questionHe = qHe,
                    optionsEn = optsList.take(4),
                    correctIndex = corrIdx,
                    explanationHe = obj.optString("explanationHe", "זוהי הצורה הדקדוקית הנכונה במשפט זה.")
                )
            )
        }

        onlineQuiz.getOrElse {
            // מאגר שאלות אינטראקטיביות מגוון ומוכן למקרה של חוסר קליטה
            val fallbackQuizzes = listOf(
                InteractiveChatQuiz(
                    typeTitleHe = typeTitleHe,
                    questionEn = "By the time we arrived at the airport, the flight _______ already departed.",
                    questionHe = "עד שהגענו לשדה התעופה, הטיסה כבר המריאה.",
                    optionsEn = listOf("had", "has", "was", "did"),
                    correctIndex = 0,
                    explanationHe = "משתמשים ב-Past Perfect (had departed) עבור פעולה שהסתיימה בעבר לפני פעולה אחרת בעבר (arrived)."
                ),
                InteractiveChatQuiz(
                    typeTitleHe = typeTitleHe,
                    questionEn = "I am really looking forward to _______ from you soon.",
                    questionHe = "אני ממש מצפה לשמוע ממך בקרוב.",
                    optionsEn = listOf("hearing", "hear", "heard", "be hear"),
                    correctIndex = 0,
                    explanationHe = "אחרי הביטוי 'look forward to' ה-to היא מילת יחס, ולכן הפועל אחריה חייב להסתיים ב-ing (hearing)!"
                ),
                InteractiveChatQuiz(
                    typeTitleHe = typeTitleHe,
                    questionEn = "Which sentence is 100% grammatically correct in English?",
                    questionHe = "איזה מהמשפטים הבאים תקין ב-100% דקדוקית באנגלית?",
                    optionsEn = listOf(
                        "I completely agree with your point.",
                        "I am completely agree with your point.",
                        "I am agreeing to your point yesterday.",
                        "Me agree with you very much."
                    ),
                    correctIndex = 0,
                    explanationHe = "הפועל agree הוא פועל רגיל ולא שם תואר, ולכן אומרים 'I agree' ולעולם לא 'I am agree'."
                ),
                InteractiveChatQuiz(
                    typeTitleHe = typeTitleHe,
                    questionEn = "If I _______ more free time this week, I would learn another programming language.",
                    questionHe = "אילו היה לי יותר זמן פנוי השבוע, הייתי לומד שפת תכנות נוספת.",
                    optionsEn = listOf("had", "will have", "have", "would have"),
                    correctIndex = 0,
                    explanationHe = "במשפט תנאי היפותטי בהווה (Second Conditional), בחלק של ה-If משתמשים בעבר פשוט (had) ובחלק השני ב-would."
                )
            )
            val chosen = fallbackQuizzes.random()
            FreeChatResponse(
                replyEn = "Here is a live interactive challenge for you! Tap the correct option below:",
                correctionHe = null,
                interactiveQuiz = chosen
            )
        }
    }

    private fun buildSmartDynamicScenarioJson(topicHe: String, difficultyHe: String): JSONObject {
        val t = topicHe.trim()
        return JSONObject().apply {
            put("id", "ai_${System.currentTimeMillis()}")
            put("categoryHe", "שיחות AI")
            put("titleHe", t)
            put("titleEn", "Interactive English Roleplay Session")
            put("difficultyHe", difficultyHe)

            val nodes = JSONArray()
            nodes.put(JSONObject().apply {
                put("id", "start")
                put("speakerName", "Service Coordinator")
                put("speakerLineEn", "Hello and welcome! Could you please explain the exact details so I can assist you right away?")
                put("speakerLineHe", "שלום וברוך הבא! האם תוכל להסביר את הפרטים המדויקים כדי שאוכל לסייע לך מיד?")
                put("options", JSONArray().apply {
                    put(JSONObject().apply {
                        put("textEn", "Hi! Thank you for seeing me. Let me explain my situation clearly and what I am hoping to resolve today.")
                        put("textHe", "היי! תודה שקיבלת אותי. תן לי להסביר את המצב שלי בבירור ומה אני מקווה לפתור היום.")
                        put("nextNodeId", "step2")
                        put("feedbackHe", "פתיחה מנומסת, רהוטה וברורה מאוד באנגלית!")
                        put("isOptimal", true)
                    })
                    put(JSONObject().apply {
                        put("textEn", "I am not know why I am standing in this place now.")
                        put("textHe", "אני לא יודע למה אני עומד במקום הזה עכשיו.")
                        put("nextNodeId", "start")
                        put("feedbackHe", "שגיאת דקדוק! באנגלית לא אומרים 'I am not know' אלא 'I do not know'.")
                        put("isOptimal", false)
                    })
                })
            })
            nodes.put(JSONObject().apply {
                put("id", "step2")
                put("speakerName", "Representative")
                put("speakerLineEn", "All set! Your official confirmation has been sent to your email. Thank you, and have a wonderful day!")
                put("speakerLineHe", "הכל מוכן! האישור הרשמי שלך נשלח לאימייל. תודה רבה ושיהיה לך יום נפלא!")
                put("isEnding", true)
                put("options", JSONArray())
            })
            put("nodes", nodes)
        }
    }

    suspend fun generateOnlineAiScenario(topicPrompt: String, difficultyHe: String): Result<ConversationScenario> = withContext(Dispatchers.IO) {
        runCatching {
            val onlineAttempt = runCatching {
                val systemPrompt = """
                    Return ONLY raw valid JSON (no markdown) for a 5-stage English roleplay scenario for Hebrew speakers.
                    "speakerLineEn" and "textEn" MUST be 100% pure English.
                    Include full Hebrew translations in "speakerLineHe" and "textHe".
                    Nodes: "start", "step2", "step3", "step4", "ending".
                """.trimIndent()

                val userPrompt = """
                    Topic: "$topicPrompt", Difficulty: "$difficultyHe".
                    JSON format:
                    {"id":"ai_1","categoryHe":"שיחות AI","titleHe":"$topicPrompt","titleEn":"English Roleplay","difficultyHe":"$difficultyHe","nodes":[{"id":"start","speakerName":"Officer","speakerLineEn":"Pure English sentence...","speakerLineHe":"תרגום מלא לעברית...","isEnding":false,"options":[{"textEn":"Pure English correct response...","textHe":"תרגום לעברית...","nextNodeId":"step2","feedbackHe":"הסבר בעברית","isOptimal":true},{"textEn":"Pure English wrong response...","textHe":"תרגום לעברית...","nextNodeId":"start","feedbackHe":"הסבר בעברית","isOptimal":false}]},{"id":"ending","speakerName":"Officer","speakerLineEn":"Pure English closing...","speakerLineHe":"תרגום סיום לעברית...","isEnding":true,"options":[]}]}
                """.trimIndent()

                val responseText = requestAiJson(systemPrompt, userPrompt)
                val jsonObj = extractCleanJsonObject(responseText)
                jsonObj.put("id", "ai_${System.currentTimeMillis()}")
                jsonObj.put("categoryHe", "שיחות AI")
                if (jsonObj.optString("titleHe").isBlank()) {
                    jsonObj.put("titleHe", topicPrompt)
                }
                enrichMissingHebrewTranslations(jsonObj)
                jsonObj
            }

            val finalJsonObj = onlineAttempt.getOrElse {
                buildSmartDynamicScenarioJson(topicPrompt, difficultyHe)
            }

            val scenario = parseSingleScenario(finalJsonObj, isAiFallback = true)
                ?: parseSingleScenario(buildSmartDynamicScenarioJson(topicPrompt, difficultyHe), isAiFallback = true)
                ?: throw IllegalStateException("שגיאה בבניית השיחה")

            saveAiScenarioToPrefs(finalJsonObj)
            scenario
        }
    }

    suspend fun generateExtraAiOptionsForNode(
        node: DialogueNode,
        nextTargetNodeId: String
    ): Result<List<DialogueOption>> = withContext(Dispatchers.IO) {
        runCatching {
            val baseOptimal = node.options.firstOrNull { it.isOptimal }
            listOf(
                DialogueOption(
                    textEn = if (baseOptimal != null) "Certainly! ${baseOptimal.textEn}" else "Could you please clarify that point so we can proceed?",
                    textHe = if (baseOptimal != null) "בוודאי! ${baseOptimal.textHe}" else "האם תוכל להבהיר את הנקודה הזו כדי שנוכל להתקדם?",
                    nextNodeId = nextTargetNodeId,
                    feedbackHe = "ניסוח חלופי מנומס ומדויק שמקדם את השיחה!",
                    isOptimal = true
                ),
                DialogueOption(
                    textEn = "I am agree with you very much about this thing.",
                    textHe = "אני מסכים איתך מאוד על הדבר הזה.",
                    nextNodeId = node.id,
                    feedbackHe = "מלכודת דקדוק קלאסית של דוברי עברית! באנגלית 'agree' הוא פועל רגיל ולכן אומרים 'I agree' ולעולם לא 'I am agree'.",
                    isOptimal = false
                )
            )
        }
    }
}
