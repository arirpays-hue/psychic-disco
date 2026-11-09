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

class ConversationRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("conversations_prefs_v2", Context.MODE_PRIVATE)

    fun getCustomApiKey(): String = prefs.getString("custom_ai_api_key", "") ?: ""

    fun saveCustomApiKey(key: String) {
        prefs.edit().putString("custom_ai_api_key", key.trim()).apply()
    }

    fun loadScenarios(): List<ConversationScenario> {
        val list = mutableListOf<ConversationScenario>()

        // 1. טעינת שיחות שנשמרו על ידי מודל ה-AI המקוון
        val savedAiJson = prefs.getString("ai_generated_scenarios_json", "[]") ?: "[]"
        list.addAll(parseScenariosArray(savedAiJson, isAi = true))

        // 2. טעינת מאגר השיחות המובנה מתוך assets/conversations.json
        try {
            val assetsJson = context.assets.open("conversations.json").bufferedReader().use { it.readText() }
            list.addAll(parseScenariosArray(assetsJson, isAi = false))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
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
        val categoryHe = obj.optString("categoryHe", if (isAiFallback) "שיחות AI ✨" else "כללי").trim()
        val titleHe = obj.optString("titleHe").trim()
        val titleEn = obj.optString("titleEn").trim()
        val difficultyHe = obj.optString("difficultyHe", "בינוני").trim()
        val isAi = obj.optBoolean("isAiGenerated", isAiFallback)

        val nodesMap = LinkedHashMap<String, DialogueNode>()
        val nodesArr = obj.optJSONArray("nodes") ?: JSONArray()

        for (j in 0 until nodesArr.length()) {
            val nObj = nodesArr.optJSONObject(j) ?: continue
            val nodeId = nObj.optString("id").trim()
            val speakerName = nObj.optString("speakerName", "Speaker").trim()
            val speakerLineEn = nObj.optString("speakerLineEn").trim()
            val speakerLineHe = nObj.optString("speakerLineHe").trim()
            val isEnding = nObj.optBoolean("isEnding", false)

            val optionsList = mutableListOf<DialogueOption>()
            val optsArr = nObj.optJSONArray("options") ?: JSONArray()
            for (k in 0 until optsArr.length()) {
                val oObj = optsArr.optJSONObject(k) ?: continue
                val textEn = oObj.optString("textEn").trim()
                if (textEn.isNotEmpty()) {
                    optionsList.add(
                        DialogueOption(
                            textEn = textEn,
                            textHe = oObj.optString("textHe").trim(),
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

    private fun saveAiScenarioToPrefs(scenarioObj: JSONObject) {
        try {
            scenarioObj.put("isAiGenerated", true)
            val existingStr = prefs.getString("ai_generated_scenarios_json", "[]") ?: "[]"
            val existingArr = runCatching { JSONArray(existingStr) }.getOrDefault(JSONArray())
            val updatedArr = JSONArray()
            updatedArr.put(scenarioObj)
            for (i in 0 until existingArr.length()) {
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

    // --- מנוע קריאת רשת אוניברסלי ל-AI (תומך ב-Gemini API, OpenRouter, ו-Pollinations חינמי ללא מפתח) ---
    private suspend fun requestAiJson(systemPrompt: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        val customKey = getCustomApiKey()

        // אם המשתמש הזין מפתח Gemini API (מתחיל ב-AIza)
        if (customKey.startsWith("AIza")) {
            val geminiUrl = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$customKey")
            val conn = (geminiUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 25000
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

        // אם המשתמש הזין מפתח OpenRouter (מתחיל ב-sk-or-) או שימוש בשרת AI חינמי מובנה ללא צורך במפתח
        val endpointUrl = if (customKey.startsWith("sk-")) {
            URL("https://openrouter.ai/api/v1/chat/completions")
        } else {
            URL("https://text.pollinations.ai/openai")
        }

        val conn = (endpointUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            if (customKey.startsWith("sk-")) {
                setRequestProperty("Authorization", "Bearer $customKey")
            }
            connectTimeout = 25000
            readTimeout = 35000
            doOutput = true
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val body = JSONObject().apply {
            put("model", if (customKey.startsWith("sk-")) "google/gemini-2.5-flash" else "openai")
            put("messages", messages)
            put("temperature", 0.8)
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
        val rawResponse = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }

        // פענוח התשובה מפורמט OpenAI/Pollinations
        val trimmed = rawResponse.trim()
        if (trimmed.startsWith("{") && trimmed.contains("\"choices\"")) {
            val root = JSONObject(trimmed)
            return@withContext root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
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
        throw IllegalArgumentException("Invalid JSON format from AI")
    }

    private fun extractCleanJsonArray(raw: String): JSONArray {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        val firstBracket = cleaned.indexOf('[')
        val lastBracket = cleaned.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket > firstBracket) {
            return JSONArray(cleaned.substring(firstBracket, lastBracket + 1))
        }
        throw IllegalArgumentException("Invalid JSON Array format from AI")
    }

    // 1. יצירת תרחיש שיחה שלם וארוך באמצעות ה-AI
    suspend fun generateOnlineAiScenario(topicPrompt: String, difficultyHe: String): Result<ConversationScenario> {
        return runCatching {
            val systemPrompt = """
                You are an expert English tutor for Hebrew speakers.
                Return ONLY a valid JSON object representing a multi-step interactive English conversation scenario.
                No markdown formatting, no explanations outside JSON.
                The scenario MUST have 6 nodes: "start", "step2", "step3", "step4", "step5", and "ending".
                In steps "start" through "step5", provide 4 options:
                - Exactly ONE option with "isOptimal": true (natural, polite, grammatically correct English, whose "nextNodeId" points to the next step).
                - Exactly THREE options with "isOptimal": false (common Hebrew-speaker grammar mistakes, literal translation traps, rude phrasing, or off-topic replies, whose "nextNodeId" stays on the current step ID).
                - Every option MUST have a detailed Hebrew explanation in "feedbackHe" starting with "✓ " for correct or "✗ " explaining the exact grammar/context mistake in Hebrew.
                Node "ending" has "isEnding": true and "options": [].
            """.trimIndent()

            val userPrompt = """
                Create a realistic 6-stage English roleplay conversation about: "$topicPrompt".
                Difficulty level in Hebrew: "$difficultyHe".
                Required JSON structure:
                {
                  "id": "ai_${System.currentTimeMillis()}",
                  "categoryHe": "שיחות AI ✨",
                  "titleHe": "כותרת בעברית לנושא",
                  "titleEn": "English Title",
                  "difficultyHe": "$difficultyHe",
                  "nodes": [
                    {
                      "id": "start",
                      "speakerName": "Role Name",
                      "speakerLineEn": "English line...",
                      "speakerLineHe": "תרגום לעברית...",
                      "isEnding": false,
                      "options": [
                        {
                          "textEn": "Correct English response...",
                          "textHe": "תרגום לעברית...",
                          "nextNodeId": "step2",
                          "feedbackHe": "✓ הסבר בעברית למה התשובה מצוינת",
                          "isOptimal": true
                        },
                        {
                          "textEn": "Wrong English response with grammar/context mistake...",
                          "textHe": "תרגום לעברית...",
                          "nextNodeId": "start",
                          "feedbackHe": "✗ הסבר מפורט בעברית על השגיאה",
                          "isOptimal": false
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()

            val responseText = requestAiJson(systemPrompt, userPrompt)
            val jsonObj = extractCleanJsonObject(responseText)
            jsonObj.put("id", "ai_${System.currentTimeMillis()}")
            jsonObj.put("categoryHe", "שיחות AI ✨")

            val scenario = parseSingleScenario(jsonObj, isAiFallback = true)
                ?: throw IllegalStateException("לא ניתן היה לפענח את השיחה מה-AI")

            saveAiScenarioToPrefs(jsonObj)
            scenario
        }
    }

    // 2. הוספת אפשרויות תשובה חדשות (נכונה + מסיחים שגויים) לשלב קיים בשיחה בזמן אמת
    suspend fun generateExtraAiOptionsForNode(
        node: DialogueNode,
        nextTargetNodeId: String
    ): Result<List<DialogueOption>> {
        return runCatching {
            val systemPrompt = """
                You are an English teacher for Hebrew speakers.
                Return ONLY a valid JSON array of 3 new response options for the given speaker line:
                - 1 option with "isOptimal": true (natural, polite alternative correct answer, "nextNodeId": "$nextTargetNodeId").
                - 2 options with "isOptimal": false (tricky grammar or vocabulary mistakes typical for Hebrew speakers, "nextNodeId": "${node.id}").
                Include Hebrew translation ("textHe") and detailed Hebrew feedback ("feedbackHe").
            """.trimIndent()

            val userPrompt = """
                Speaker (${node.speakerName}) says: "${node.speakerLineEn}"
                Generate a JSON array of 3 new distinct options:
                [
                  {
                    "textEn": "...",
                    "textHe": "...",
                    "nextNodeId": "$nextTargetNodeId",
                    "feedbackHe": "✓ ...",
                    "isOptimal": true
                  },
                  {
                    "textEn": "...",
                    "textHe": "...",
                    "nextNodeId": "${node.id}",
                    "feedbackHe": "✗ ...",
                    "isOptimal": false
                  }
                ]
            """.trimIndent()

            val responseText = requestAiJson(systemPrompt, userPrompt)
            val arr = extractCleanJsonArray(responseText)
            val newOptions = mutableListOf<DialogueOption>()

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val isOpt = o.optBoolean("isOptimal", false)
                newOptions.add(
                    DialogueOption(
                        textEn = o.optString("textEn").trim(),
                        textHe = o.optString("textHe").trim(),
                        nextNodeId = if (isOpt) nextTargetNodeId else node.id,
                        feedbackHe = o.optString("feedbackHe").trim(),
                        isOptimal = isOpt
                    )
                )
            }
            if (newOptions.isEmpty()) throw IllegalStateException("לא התקבלו תשובות חדשות")
            newOptions
        }
    }
}
