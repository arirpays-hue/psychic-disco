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

data class FreeChatResponse(
    val replyEn: String,
    val correctionHe: String?
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
        return if (withoutHebrew.length >= 4) withoutHebrew else fallback
    }

    private fun translateEnToHeFallback(englishText: String): String {
        if (englishText.isBlank()) return ""
        return runCatching {
            val encoded = URLEncoder.encode(englishText, "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=iw&dt=t&q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 5000
                readTimeout = 5000
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
        }.getOrDefault("לחץ להשמעת המשפט באנגלית ונסה להבין מההקשר")
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

            var speakerLineHe = nObj.optString("speakerLineHe").ifBlank {
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
                        if (isOpt) "✓ תשובה מצוינת! דקדוק תקין וניסוח מנומס."
                        else "✗ תשובה שגויה דקדוקית או שאינה מתאימה להקשר השיחה. נסה שוב!"
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
                setRequestProperty("User-Agent", "EnglishTutorAndroid/4.0")
                connectTimeout = 20000
                readTimeout = 40000
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

    private fun buildSmartDynamicScenarioJson(topicHe: String, difficultyHe: String): JSONObject {
        val t = topicHe.trim()
        val isSecurityAirport = t.contains("ביטחונית") || t.contains("שדה תעופה") || t.contains("תשאול") || t.contains("טיסה")
        val isLondonApartment = t.contains("דירה") || t.contains("לונדון") || t.contains("שכיר")
        val isDentistOrDoctor = t.contains("שיניים") || t.contains("רופא") || t.contains("מרפאה")
        val isBank = t.contains("בנק") || t.contains("אשראי") || t.contains("חשבון")

        if (isSecurityAirport) {
            return JSONObject("""
                {
                  "id": "ai_${System.currentTimeMillis()}",
                  "categoryHe": "שיחות AI ✨",
                  "titleHe": "$t",
                  "titleEn": "Airport Security Screening & Questioning",
                  "difficultyHe": "$difficultyHe",
                  "nodes": [
                    {
                      "id": "start",
                      "speakerName": "Security Officer",
                      "speakerLineEn": "Good morning. Please step aside for a routine security check. Did you pack your carry-on bag yourself this morning?",
                      "speakerLineHe": "בוקר טוב. אנא זוז הצידה לבדיקה ביטחונית שגרתית. האם ארזת את תיק היד שלך בעצמך הבוקר?",
                      "options": [
                        {
                          "textEn": "Yes, officer. I packed my bag myself at the hotel, and it has been with me the entire time.",
                          "textHe": "כן, אדוני השוטר. ארזתי את התיק בעצמי במלון, והוא היה איתי כל הזמן.",
                          "nextNodeId": "step2",
                          "feedbackHe": "✓ תשובה מצוינת! ברורה, רגועה ומדויקת לתשאול ביטחוני בשדה התעופה.",
                          "isOptimal": true
                        },
                        {
                          "textEn": "A stranger in the street gave me this closed box to take on the plane.",
                          "textHe": "אדם זר ברחוב נתן לי את הקופסה הסגורה הזו כדי להעלות למטוס.",
                          "nextNodeId": "start",
                          "feedbackHe": "✗ תשובה מסוכנת שתוביל לעיכוב ביטחוני מיידי! לעולם לא לוקחים חפצים מזרים לטיסה.",
                          "isOptimal": false
                        },
                        {
                          "textEn": "I am not know who putted things inside my bag.",
                          "textHe": "אני לא יודע מי שם דברים בתוך התיק שלי.",
                          "nextNodeId": "start",
                          "feedbackHe": "✗ שתי שגיאות דקדוק ('I do not know', והפועל 'put' נשאר 'put' בעבר ולא 'putted').",
                          "isOptimal": false
                        }
                      ]
                    },
                    {
                      "id": "step2",
                      "speakerName": "Security Officer",
                      "speakerLineEn": "Do you have any laptops, power banks, or liquids over 100 milliliters inside your backpack?",
                      "speakerLineHe": "האם יש לך מחשבים ניידים, סוללות גיבוי (פאוור-בנק) או נוזלים מעל 100 מיליליטר בתוך תיק הגב?",
                      "options": [
                        {
                          "textEn": "I have my laptop and a portable charger. Shall I take them out and place them in a separate tray?",
                          "textHe": "יש לי את הלפטופ ומטען נייד. האם להוציא אותם ולהניח אותם במגש נפרד?",
                          "nextNodeId": "step3",
                          "feedbackHe": "✓ מעולה! 'Portable charger' ו-'separate tray' (מגש נפרד) הם ביטויים שימושיים מאוד בבידוק.",
                          "isOptimal": true
                        },
                        {
                          "textEn": "Yes, I have three big bottles of water and soup inside.",
                          "textHe": "כן, יש לי בפנים שלושה בקבוקים גדולים של מים ומרק.",
                          "nextNodeId": "step2",
                          "feedbackHe": "✗ בבידוק ביטחוני אסור להעביר נוזלים מעל 100 מ״ל בתיק היד.",
                          "isOptimal": false
                        },
                        {
                          "textEn": "Where I need to throw my computer?",
                          "textHe": "איפה אני צריך לזרוק את המחשב שלי?",
                          "nextNodeId": "step2",
                          "feedbackHe": "✗ שגיאת תחביר ('Where do I need to') וטעות בפועל (אומרים 'place' או 'put' במגש, לא 'throw').",
                          "isOptimal": false
                        }
                      ]
                    },
                    {
                      "id": "step3",
                      "speakerName": "Security Officer",
                      "speakerLineEn": "Yes, place all electronics in the grey bin and empty your pockets before walking through the metal detector.",
                      "speakerLineHe": "כן, הנח את כל מוצרי האלקטרוניקה במגש האפור ורוקן את הכיסים לפני שתעבור בגלאי המתכות.",
                      "options": [
                        {
                          "textEn": "My pockets are completely empty. Should I also remove my belt and watch?",
                          "textHe": "הכיסים שלי ריקים לחלוטין. האם עליי להסיר גם את החגורה והשעון?",
                          "nextNodeId": "step4",
                          "feedbackHe": "✓ אנגלית טבעית ומנומסת מאוד!",
                          "isOptimal": true
                        },
                        {
                          "textEn": "I keep my keys and coins in my pocket so the machine will sing.",
                          "textHe": "אני משאיר את המפתחות והמטבעות בכיס כדי שהמכונה תשיר.",
                          "nextNodeId": "step3",
                          "feedbackHe": "✗ השארת מתכות בכיסים תפעיל את האזעקה של גלאי המתכות (metal detector).",
                          "isOptimal": false
                        },
                        {
                          "textEn": "Why you want to steal my watch?",
                          "textHe": "למה אתה רוצה לגנוב את השעון שלי?",
                          "nextNodeId": "step3",
                          "feedbackHe": "✗ תגובה לא הולמת בבידוק ביטחוני ושגויה דקדוקית ('Why do you...').",
                          "isOptimal": false
                        }
                      ]
                    },
                    {
                      "id": "step4",
                      "speakerName": "Security Officer",
                      "speakerLineEn": "Please step forward. We need to swab your laptop for a quick trace test. It will only take thirty seconds.",
                      "speakerLineHe": "אנא התקדם. אנחנו צריכים להעביר מטוש בדיקה מהיר על הלפטופ שלך. זה ייקח רק שלושים שניות.",
                      "options": [
                        {
                          "textEn": "No problem at all, take your time. Let me know if you need me to unlock the screen.",
                          "textHe": "אין שום בעיה, קח את הזמן. עדכן אותי אם תצטרך שאפתח את נעילת המסך.",
                          "nextNodeId": "step5",
                          "feedbackHe": "✓ תשובה רגועה, משתפת פעולה ומנוסחת היטב!",
                          "isOptimal": true
                        },
                        {
                          "textEn": "Don't touch my computer or I will shout very loud!",
                          "textHe": "אל תיגע במחשב שלי או שאצעק חזק מאוד!",
                          "nextNodeId": "step4",
                          "feedbackHe": "✗ סירוב לבדיקת מטוש (swab test) בשדה תעופה ימנע ממך לעלות לטיסה.",
                          "isOptimal": false
                        }
                      ]
                    },
                    {
                      "id": "step5",
                      "speakerName": "Security Officer",
                      "speakerLineEn": "The test is clear. You can pack your belongings back into your bag. What gate is your flight departing from?",
                      "speakerLineHe": "הבדיקה תקינה. אתה יכול לארוז חזרה את החפצים שלך לתיק. מאיזה שער הטיסה שלך ממריאה?",
                      "options": [
                        {
                          "textEn": "Thank you, officer! My flight departs from Gate C14, and boarding starts in forty minutes.",
                          "textHe": "תודה לך, אדוני השוטר! הטיסה שלי ממריאה משער C14, והעלייה למטוס מתחילה בעוד ארבעים דקות.",
                          "nextNodeId": "ending",
                          "feedbackHe": "✓ מושלם! 'Departs from Gate...' ו-'boarding starts' הם ביטויי תעופה מדויקים.",
                          "isOptimal": true
                        },
                        {
                          "textEn": "My airplane is flying from the roof yesterday.",
                          "textHe": "המטוס שלי טס מהגג אתמול.",
                          "nextNodeId": "step5",
                          "feedbackHe": "✗ משפט חסר היגיון ושגוי בזמנים.",
                          "isOptimal": false
                        }
                      ]
                    },
                    {
                      "id": "ending",
                      "speakerName": "Security Officer",
                      "speakerLineEn": "Gate C14 is straight ahead to the right. Have a safe and pleasant flight!",
                      "speakerLineHe": "שער C14 נמצא ישר ומימין. שתהיה לך טיסה בטוחה ונעימה!",
                      "isEnding": true,
                      "options": []
                    }
                  ]
                }
            """.trimIndent())
        }

        if (isLondonApartment) {
            return JSONObject("""
                {
                  "id": "ai_${System.currentTimeMillis()}",
                  "categoryHe": "שיחות AI ✨",
                  "titleHe": "$t",
                  "titleEn": "Negotiating an Apartment Lease in London",
                  "difficultyHe": "$difficultyHe",
                  "nodes": [
                    {
                      "id": "start",
                      "speakerName": "London Estate Agent",
                      "speakerLineEn": "Welcome to the flat in Camden! The asking rent is £2,100 per month, excluding council tax and utilities. What do you think?",
                      "speakerLineHe": "ברוך הבא לדירה בקמדן! שכר הדירה המבוקש הוא 2,100 פאונד לחודש, לא כולל ארנונה וחשבונות. מה דעתך?",
                      "options": [
                        {
                          "textEn": "The flat is lovely, but £2,100 is slightly above my budget. Would the landlord consider £1,900 if I sign a long-term lease?",
                          "textHe": "הדירה מקסימה, אבל 2,100 פאונד זה מעט מעל התקציב שלי. האם בעל הדירה ישקול 1,900 פאונד אם אחתום על חוזה לטווח ארוך?",
                          "nextNodeId": "step2",
                          "feedbackHe": "✓ פתיחת משא ומתן מנומסת ומשכנעת מאוד באנגלית בריטית!",
                          "isOptimal": true
                        },
                        {
                          "textEn": "You are crazy! This price is robbery in the middle of the day!",
                          "textHe": "אתה משוגע! המחיר הזה הוא שוד לאור יום!",
                          "nextNodeId": "start",
                          "feedbackHe": "✗ פנייה תוקפנית ולא מקצועית שתגרום לסוכן הנדל״ן לדחות את ההצעה שלך מיד.",
                          "isOptimal": false
                        },
                        {
                          "textEn": "Yes, I want to buy the council tax for two thousand pounds.",
                          "textHe": "כן, אני רוצה לקנות את הארנונה באלפיים פאונד.",
                          "nextNodeId": "start",
                          "feedbackHe": "✗ אי הבנת המושג: Council tax היא ארנונה עירונית בבריטניה, לא משהו שקונים.",
                          "isOptimal": false
                        }
                      ]
                    },
                    {
                      "id": "step2",
                      "speakerName": "London Estate Agent",
                      "speakerLineEn": "The landlord might compromise at £1,950 if you can move in on the 1st of next month and provide employment references.",
                      "speakerLineHe": "בעל הדירה עשוי להתפשר על 1,950 פאונד אם תוכל להיכנס ב-1 לחודש הבא ולספק המלצות ממקום העבודה.",
                      "options": [
                        {
                          "textEn": "That sounds fair! I have my employment contract and bank statements ready, provided the landlord replaces the old mattress.",
                          "textHe": "זה נשמע הוגן! חוזה ההעסקה ודפי הבנק שלי מוכנים, בתנאי שבעל הדירה יחליף את המזרן הישן.",
                          "nextNodeId": "step3",
                          "feedbackHe": "✓ מצוין! השתמשת נכון ב-'provided' (בתנאי ש...) להצבת תנאי נגדי במשא ומתן.",
                          "isOptimal": true
                        },
                        {
                          "textEn": "I don't have any job and I never pay rent on time.",
                          "textHe": "אין לי שום עבודה ואני אף פעם לא משלם שכר דירה בזמן.",
                          "nextNodeId": "step2",
                          "feedbackHe": "✗ תשובה שתפסול אותך מיידית מבדיקת הרקע (Referencing) של הסוכנות.",
                          "isOptimal": false
                        }
                      ]
                    },
                    {
                      "id": "step3",
                      "speakerName": "London Estate Agent",
                      "speakerLineEn": "Agreed, a brand-new mattress will be delivered before you move in. Now, let's discuss the five-week security deposit and the break clause.",
                      "speakerLineHe": "מוסכם, מזרן חדש לגמרי יסופק לפני שתיכנס. כעת, בוא נדון בפיקדון של חמישה שבועות ובסעיף היציאה מהחוזה.",
                      "options": [
                        {
                          "textEn": "Will my deposit be protected in a government-approved scheme, and can we include a six-month break clause?",
                          "textHe": "האם הפיקדון שלי יהיה מוגן בתוכנית ממשלתית מאושרת, והאם נוכל לכלול סעיף יציאה אחרי חצי שנה?",
                          "nextNodeId": "ending",
                          "feedbackHe": "✓ אלוף! 'Break clause' (סעיף יציאה) ו-'deposit protection scheme' הם מונחי מפתח בשכירות בלונדון.",
                          "isOptimal": true
                        },
                        {
                          "textEn": "Please break the windows after six months.",
                          "textHe": "בבקשה תשבור את החלונות אחרי שישה חודשים.",
                          "nextNodeId": "step3",
                          "feedbackHe": "✗ מלכודת מילים! 'Break clause' הוא סעיף יציאה מוקדמת מחוזה, לא שבירת חפצים.",
                          "isOptimal": false
                        }
                      ]
                    },
                    {
                      "id": "ending",
                      "speakerName": "London Estate Agent",
                      "speakerLineEn": "Brilliant! I've just sent the tenancy agreement to your inbox at the reduced rate of £1,950. Welcome to your new London home!",
                      "speakerLineHe": "נהדר! שלחתי כעת את הסכם השכירות לתיבת המייל שלך בתעריף המופחת של 1,950 פאונד. ברוך הבא לביתך החדש בלונדון!",
                      "isEnding": true,
                      "options": []
                    }
                  ]
                }
            """.trimIndent())
        }

        return JSONObject().apply {
            put("id", "ai_${System.currentTimeMillis()}")
            put("categoryHe", "שיחות AI ✨")
            put("titleHe", t)
            put("titleEn", "Interactive English Roleplay Session")
            put("difficultyHe", difficultyHe)

            val nodes = JSONArray()

            nodes.put(JSONObject().apply {
                put("id", "start")
                put("speakerName", if (isDentistOrDoctor) "Specialist Doctor" else if (isBank) "Bank Advisor" else "Service Coordinator")
                put("speakerLineEn", "Hello and welcome! I understand you are here regarding an urgent matter today. Could you please explain the exact details so I can assist you right away?")
                put("speakerLineHe", "שלום וברוך הבא! אני מבין שהגעת בנוגע לעניין חשוב היום ($t). האם תוכל להסביר את הפרטים המדויקים כדי שאוכל לסייע לך מיד?")
                put("options", JSONArray().apply {
                    put(JSONObject().apply {
                        put("textEn", "Hi! Thank you for seeing me. Let me explain my situation clearly and what I am hoping to resolve today.")
                        put("textHe", "היי! תודה שקיבלת אותי. תן לי להסביר את המצב שלי בבירור ומה אני מקווה לפתור היום.")
                        put("nextNodeId", "step2")
                        put("feedbackHe", "✓ פתיחה מנומסת, רהוטה וברורה מאוד באנגלית!")
                        put("isOptimal", true)
                    })
                    put(JSONObject().apply {
                        put("textEn", "I am not know why I am standing in this place now.")
                        put("textHe", "אני לא יודע למה אני עומד במקום הזה עכשיו.")
                        put("nextNodeId", "start")
                        put("feedbackHe", "✗ שגיאת דקדוק! באנגלית לא אומרים 'I am not know' אלא 'I do not know'.")
                        put("isOptimal", false)
                    })
                    put(JSONObject().apply {
                        put("textEn", "Give me solution now without asking questions!")
                        put("textHe", "תן לי פתרון עכשיו בלי לשאול שאלות!")
                        put("nextNodeId", "start")
                        put("feedbackHe", "✗ פנייה בוטה מדי וחסרת תווית יידוע ('a solution').")
                        put("isOptimal", false)
                    })
                })
            })

            nodes.put(JSONObject().apply {
                put("id", "step2")
                put("speakerName", "Representative")
                put("speakerLineEn", "I see what you mean. We have two options available: a standard procedure that takes three business days, or an express priority track. Which do you prefer?")
                put("speakerLineHe", "אני מבין למה אתה מתכוון. יש לנו שתי אפשרויות זמינות: הליך רגיל שלוקח שלושה ימי עסקים, או מסלול אקספרס מהיר. מה אתה מעדיף?")
                put("options", JSONArray().apply {
                    put(JSONObject().apply {
                        put("textEn", "I'm on a tight schedule, so I'd prefer the express priority track. How much does it cost, and is there any extra fee?")
                        put("textHe", "אני בלוח זמנים צפוף, לכן אעדיף את מסלול האקספרס המהיר. כמה זה עולה והאם יש עמלה נוספת?")
                        put("nextNodeId", "step3")
                        put("feedbackHe", "✓ מעולה! 'On a tight schedule' (בלו״ז צפוף) הוא ביטוי טבעי ושימושי מאוד.")
                        put("isOptimal", true)
                    })
                    put(JSONObject().apply {
                        put("textEn", "How many moneys is the fast thing?")
                        put("textHe", "כמה כספים זה הדבר המהיר?")
                        put("nextNodeId", "step2")
                        put("feedbackHe", "✗ שגיאה! המילה money אינה ספירה באנגלית—שואלים 'How much does it cost?' ולא 'How many moneys'.")
                        put("isOptimal", false)
                    })
                })
            })

            nodes.put(JSONObject().apply {
                put("id", "step3")
                put("speakerName", "Representative")
                put("speakerLineEn", "There is normally a $35 express fee, but since this is your first visit, I can waive half of the fee if we complete the paperwork now.")
                put("speakerLineHe", "בדרך כלל יש עמלת אקספרס של 35 דולר, אך מאחר שזה הביקור הראשון שלך, אוכל לוותר על מחצית מהעמלה אם נשלים את הטפסים כעת.")
                put("options", JSONArray().apply {
                    put(JSONObject().apply {
                        put("textEn", "I really appreciate that! What identification or documents do you need from me to finalize the process?")
                        put("textHe", "אני ממש מעריך את זה! אילו מסמכים או אמצעי זיהוי אתה צריך ממני כדי להשלים את התהליך?")
                        put("nextNodeId", "step4")
                        put("feedbackHe", "✓ מצוין! הבנת שהפועל 'waive' פירושו לוותר על עמלה/תשלום, והגבת בנימוס.")
                        put("isOptimal", true)
                    })
                    put(JSONObject().apply {
                        put("textEn", "Why you wave your hand at me for 35 dollars?")
                        put("textHe", "למה אתה מנופף לי ביד בשביל 35 דולר?")
                        put("nextNodeId", "step3")
                        put("feedbackHe", "✗ מלכודת מילים שנשמעות זהה (Homophones)! המילה 'waive' פירושה לוותר על תשלום/חוב, בעוד ש-'wave' היא לנופף ביד.")
                        put("isOptimal", false)
                    })
                })
            })

            nodes.put(JSONObject().apply {
                put("id", "step4")
                put("speakerName", "Representative")
                put("speakerLineEn", "Everything is entered into our system. Please review the summary on the screen and confirm that your contact details are accurate.")
                put("speakerLineHe", "הכל הוזן למערכת שלנו. אנא עבור על הסיכום שעל המסך ואשר שפרטי הקשר שלך מדויקים.")
                put("options", JSONArray().apply {
                    put(JSONObject().apply {
                        put("textEn", "I've double-checked the spelling of my name and email address—everything looks completely accurate!")
                        put("textHe", "בדקתי פעמיים את איות השם וכתובת האימייל שלי — הכל נראה מדויק לחלוטין!")
                        put("nextNodeId", "ending")
                        put("feedbackHe", "✓ מושלם! 'Double-checked' הוא ביטוי מצוין לאימות פרטים.")
                        put("isOptimal", true)
                    })
                    put(JSONObject().apply {
                        put("textEn", "The screen is made of glass and plastic.")
                        put("textHe", "המסך עשוי מזכוכית ופלסטיק.")
                        put("nextNodeId", "step4")
                        put("feedbackHe", "✗ לא ענית לבקשה לאשר את נכונות הפרטים על המסך.")
                        put("isOptimal", false)
                    })
                })
            })

            nodes.put(JSONObject().apply {
                put("id", "ending")
                put("speakerName", "Representative")
                put("speakerLineEn", "All set! Your official confirmation has been sent to your email. Thank you, and have a wonderful day!")
                put("speakerLineHe", "הכל מוכן! האישור הרשמי שלך נשלח לאימייל. תודה רבה ושיהיה לך יום נפלא!")
                put("isEnding", true)
                put("options", JSONArray())
            })

            put("nodes", nodes)
        }
    }

    suspend fun generateFreeChatReply(userMessage: String, history: List<ChatMessage>): FreeChatResponse = withContext(Dispatchers.IO) {
        runCatching {
            val systemPrompt = """
                You are a friendly, conversational English tutor for Hebrew speakers.
                Respond in natural, engaging English (1-2 sentences).
                Analyze the user's sentence for grammar, phrasing, or spelling mistakes:
                - If there is a mistake, explain it constructively in Hebrew in "correctionHe".
                - If the English is completely natural and correct, set "correctionHe" to null.
                Format output strictly as JSON: {"replyEn": "...", "correctionHe": "..."}
            """.trimIndent()

            val jsonStr = requestAiJson(systemPrompt, userMessage)
            val obj = extractCleanJsonObject(jsonStr)
            val replyEn = sanitizeEnglishOnly(obj.optString("replyEn"), "That sounds very interesting! Tell me more about that.")
            val correctionHe = if (obj.has("correctionHe") && !obj.isNull("correctionHe") && obj.getString("correctionHe").isNotBlank()) {
                obj.getString("correctionHe")
            } else null

            FreeChatResponse(replyEn, correctionHe)
        }.getOrElse {
            FreeChatResponse("That sounds really interesting! Tell me more about that.", null)
        }
    }

    suspend fun generateOnlineAiScenario(topicPrompt: String, difficultyHe: String): Result<ConversationScenario> = withContext(Dispatchers.IO) {
        runCatching {
            val onlineAttempt = runCatching {
                val systemPrompt = """
                    Return ONLY raw valid JSON (no markdown) for a 5-stage English roleplay scenario for Hebrew speakers.
                    IMPORTANT RULES:
                    1. "speakerLineEn" and "textEn" MUST be 100% pure English (NEVER put Hebrew words inside English sentences).
                    2. Every node MUST have a complete Hebrew translation in "speakerLineHe", and every option MUST have a complete Hebrew translation in "textHe".
                    3. Nodes: "start", "step2", "step3", "step4", "ending".
                    4. Each non-ending node must have 3 options: 1 with "isOptimal": true ("nextNodeId" -> next step) and 2 with "isOptimal": false ("nextNodeId" -> same step).
                """.trimIndent()

                val userPrompt = """
                    Topic: "$topicPrompt", Difficulty: "$difficultyHe".
                    JSON format:
                    {"id":"ai_1","categoryHe":"שיחות AI ✨","titleHe":"$topicPrompt","titleEn":"English Roleplay","difficultyHe":"$difficultyHe","nodes":[{"id":"start","speakerName":"Officer","speakerLineEn":"Pure English sentence...","speakerLineHe":"תרגום מלא לעברית...","isEnding":false,"options":[{"textEn":"Pure English correct response...","textHe":"תרגום לעברית...","nextNodeId":"step2","feedbackHe":"✓ הסבר בעברית","isOptimal":true},{"textEn":"Pure English wrong response...","textHe":"תרגום לעברית...","nextNodeId":"start","feedbackHe":"✗ הסבר בעברית","isOptimal":false}]},{"id":"ending","speakerName":"Officer","speakerLineEn":"Pure English closing...","speakerLineHe":"תרגום סיום לעברית...","isEnding":true,"options":[]}]}
                """.trimIndent()

                val responseText = requestAiJson(systemPrompt, userPrompt)
                val jsonObj = extractCleanJsonObject(responseText)
                jsonObj.put("id", "ai_${System.currentTimeMillis()}")
                jsonObj.put("categoryHe", "שיחות AI ✨")
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
            val onlineAttempt = runCatching {
                val systemPrompt = "Return ONLY a valid JSON array of 2 pure English response options for Hebrew speakers (with full Hebrew 'textHe' and 'feedbackHe'): 1 optimal (true) and 1 grammar mistake trap (false)."
                val userPrompt = """
                    Speaker (${node.speakerName}) says: "${node.speakerLineEn}"
                    JSON array format:
                    [{"textEn":"Pure English...","textHe":"תרגום לעברית...","nextNodeId":"$nextTargetNodeId","feedbackHe":"✓ ...","isOptimal":true},{"textEn":"Pure English mistake...","textHe":"תרגום לעברית...","nextNodeId":"${node.id}","feedbackHe":"✗ ...","isOptimal":false}]
                """.trimIndent()

                val responseText = requestAiJson(systemPrompt, userPrompt)
                val arr = extractCleanJsonArray(responseText)
                val list = mutableListOf<DialogueOption>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val isOpt = o.optBoolean("isOptimal", false)
                    val en = sanitizeEnglishOnly(o.optString("textEn").trim(), "")
                    var he = o.optString("textHe").trim()
                    if (en.isNotEmpty() && he.isEmpty()) {
                        he = translateEnToHeFallback(en)
                    }
                    if (en.isNotEmpty()) {
                        list.add(
                            DialogueOption(
                                textEn = en,
                                textHe = he,
                                nextNodeId = if (isOpt) nextTargetNodeId else node.id,
                                feedbackHe = o.optString("feedbackHe").ifBlank {
                                    if (isOpt) "✓ תשובה חלופית מצוינת!" else "✗ שגיאת דקדוק או הקשר."
                                }.trim(),
                                isOptimal = isOpt
                            )
                        )
                    }
                }
                if (list.isEmpty()) throw IllegalStateException("Empty options")
                list
            }

            onlineAttempt.getOrElse {
                val baseOptimal = node.options.firstOrNull { it.isOptimal }
                listOf(
                    DialogueOption(
                        textEn = if (baseOptimal != null) "Certainly! ${baseOptimal.textEn}" else "Could you please clarify that point so we can proceed?",
                        textHe = if (baseOptimal != null) "בוודאי! ${baseOptimal.textHe}" else "האם תוכל להבהיר את הנקודה הזו כדי שנוכל להתקדם?",
                        nextNodeId = nextTargetNodeId,
                        feedbackHe = "✓ ניסוח חלופי מנומס ומדויק שמקדם את השיחה!",
                        isOptimal = true
                    ),
                    DialogueOption(
                        textEn = "I am agree with you very much about this thing.",
                        textHe = "אני מסכים איתך מאוד על הדבר הזה.",
                        nextNodeId = node.id,
                        feedbackHe = "✗ מלכודת דקדוק קלאסית של דוברי עברית! באנגלית 'agree' הוא פועל רגיל ולכן אומרים 'I agree' ולעולם לא 'I am agree'.",
                        isOptimal = false
                    )
                )
            }
        }
    }
}
