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

class ConversationRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("conversations_prefs_v2", Context.MODE_PRIVATE)

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

    // --- קריאת רשת מהירה עם מעבר אוטומטי בין שרתים למניעת Timeout ---
    private suspend fun requestAiJson(systemPrompt: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        val customKey = getCustomApiKey()

        // 1. אם הוגדר מפתח Gemini API אישי (AIza...)
        if (customKey.startsWith("AIza")) {
            val geminiUrl = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$customKey")
            val conn = (geminiUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "EnglishTutorAndroid/4.0")
                connectTimeout = 20000
                readTimeout = 45000
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

        // 2. ניסיון ראשון: POST מהיר לשרת Pollinations OpenAI או OpenRouter
        val postAttempt = runCatching {
            val endpointUrl = if (customKey.startsWith("sk-")) {
                URL("https://openrouter.ai/api/v1/chat/completions")
            } else {
                URL("https://text.pollinations.ai/openai")
            }

            val conn = (endpointUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                if (customKey.startsWith("sk-")) {
                    setRequestProperty("Authorization", "Bearer $customKey")
                }
                connectTimeout = 15000
                readTimeout = 28000
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
                root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } else {
                trimmed
            }
        }

        if (postAttempt.isSuccess) {
            val text = postAttempt.getOrThrow()
            if (text.contains("{") || text.contains("[")) return@withContext text
        }

        // 3. ניסיון שני: נקודת קצה ישירה ומהירה ב-GET
        val combinedPrompt = URLEncoder.encode("$systemPrompt\n$userPrompt", "UTF-8")
        val getUrl = URL("https://text.pollinations.ai/$combinedPrompt?model=openai-fast&json=true")
        val getConn = (getUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            connectTimeout = 12000
            readTimeout = 22000
        }
        return@withContext BufferedReader(InputStreamReader(getConn.inputStream, Charsets.UTF_8)).use { it.readText() }
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

    // --- מנוע גיבוי חכם ומיידי שבונה שיחה מלאה של 6 שלבים על כל נושא אם השרת החיצוני ב-Timeout ---
    private fun buildSmartDynamicScenarioJson(topicHe: String, difficultyHe: String): JSONObject {
        val t = topicHe.trim()
        val isLondonApartment = t.contains("דירה") || t.contains("לונדון") || t.contains("שכיר")
        val isDentistOrDoctor = t.contains("שיניים") || t.contains("רופא") || t.contains("מרפאה")
        val isBank = t.contains("בנק") || t.contains("אשראי") || t.contains("חשבון")

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
                        },
                        {
                          "textEn": "When the landlord will to move into my room?",
                          "textHe": "מתי בעל הדירה יעבור לתוך החדר שלי?",
                          "nextNodeId": "step2",
                          "feedbackHe": "✗ שגיאת תחביר ('will to move') ואי הבנת המשפט: הסוכן שאל מתי אתה יכול להיכנס לדירה.",
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
                          "nextNodeId": "step4",
                          "feedbackHe": "✓ אלוף! 'Break clause' (סעיף יציאה) ו-'deposit protection scheme' הם מונחי מפתח בשכירות בלונדון.",
                          "isOptimal": true
                        },
                        {
                          "textEn": "Please break the windows after six months.",
                          "textHe": "בבקשה תשבור את החלונות אחרי שישה חודשים.",
                          "nextNodeId": "step3",
                          "feedbackHe": "✗ מלכודת מילים! 'Break clause' הוא סעיף יציאה מוקדמת מחוזה, לא שבירת חפצים.",
                          "isOptimal": false
                        },
                        {
                          "textEn": "I give you cash in dark street without receipt.",
                          "textHe": "אני נותן לך מזומן ברחוב חשוך בלי קבלה.",
                          "nextNodeId": "step3",
                          "feedbackHe": "✗ שגוי ומסוכן! פיקדון שכירות מפקידים רק לחשבון מוגן עם אסמכתא רשמית.",
                          "isOptimal": false
                        }
                      ]
                    },
                    {
                      "id": "step4",
                      "speakerName": "London Estate Agent",
                      "speakerLineEn": "Yes, the deposit goes straight to the Tenancy Deposit Scheme, and a 6-month break clause is fine. Do you have any questions about the inventory check?",
                      "speakerLineHe": "כן, הפיקדון עובר ישירות לתוכנית הגנת הפיקדונות, וסעיף יציאה אחרי 6 חודשים מקובל. יש לך שאלות לגבי בדיקת המלאי ומצב הדירה?",
                      "options": [
                        {
                          "textEn": "Yes, I want to make sure all existing marks on the walls are photographed during check-in so I won't be charged later.",
                          "textHe": "כן, אני רוצה לוודא שכל הסימנים הקיימים על הקירות יצולמו בעת הכניסה כדי שלא אחויב עליהם בהמשך.",
                          "nextNodeId": "step5",
                          "feedbackHe": "✓ חשיבה צרכנית חכמה ואנגלית מדויקת ('so I won't be charged later').",
                          "isOptimal": true
                        },
                        {
                          "textEn": "I will paint the ceiling with black oil tomorrow.",
                          "textHe": "אני אצבע את התקרה בשמן שחור מחר.",
                          "nextNodeId": "step4",
                          "feedbackHe": "✗ תשובה לא מתאימה לסיטואציה.",
                          "isOptimal": false
                        },
                        {
                          "textEn": "What is the price of the airplane ticket?",
                          "textHe": "מה המחיר של כרטיס הטיסה?",
                          "nextNodeId": "step4",
                          "feedbackHe": "✗ לא קשור לנושא השיחה.",
                          "isOptimal": false
                        }
                      ]
                    },
                    {
                      "id": "step5",
                      "speakerName": "London Estate Agent",
                      "speakerLineEn": "An independent clerk will document every detail. How would you like to proceed with signing the tenancy agreement?",
                      "speakerLineHe": "בודק עצמאי יתעד כל פרט. איך תרצה להתקדם עם החתימה על הסכם השכירות?",
                      "options": [
                        {
                          "textEn": "Please email me the final draft of the tenancy agreement so I can review the clauses and sign it digitally today.",
                          "textHe": "אנא שלח לי במייל את הטיוטה הסופית של הסכם השכירות כדי שאוכל לעבור על הסעיפים ולחתום דיגיטלית היום.",
                          "nextNodeId": "ending",
                          "feedbackHe": "✓ סגירת עסקה מושלמת ומקצועית!",
                          "isOptimal": true
                        },
                        {
                          "textEn": "I never read contracts, just take my wallet.",
                          "textHe": "אני אף פעם לא קורא חוזים, פשוט קח את הארנק שלי.",
                          "nextNodeId": "step5",
                          "feedbackHe": "✗ תמיד מבקשים לעבור על הטיוטה ('review the final draft') לפני החתימה.",
                          "isOptimal": false
                        }
                      ]
                    },
                    {
                      "id": "ending",
                      "speakerName": "London Estate Agent",
                      "speakerLineEn": "Brilliant! I've just sent the contract to your inbox at the reduced rate of £1,950. Welcome to your new London home!",
                      "speakerLineHe": "נהדר! שלחתי כעת את החוזה לתיבת המייל שלך בתעריף המופחת של 1,950 פאונד. ברוך הבא לביתך החדש בלונדון!",
                      "isEnding": true,
                      "options": []
                    }
                  ]
                }
            """.trimIndent())
        }

        // תבנית דינמית חכמה שמייצרת שיחה ארוכה של 6 שלבים לכל נושא שהמשתמש מקליד
        return JSONObject().apply {
            put("id", "ai_${System.currentTimeMillis()}")
            put("categoryHe", "שיחות AI ✨")
            put("titleHe", t)
            put("titleEn", "Interactive AI Roleplay: $t")
            put("difficultyHe", difficultyHe)

            val nodes = JSONArray()

            nodes.put(JSONObject().apply {
                put("id", "start")
                put("speakerName", if (isDentistOrDoctor) "Specialist Doctor" else if (isBank) "Bank Advisor" else "Local Coordinator")
                put("speakerLineEn", "Hello! Welcome. I understand you're here regarding your request ($t). Could you explain the exact details so I can help you right away?")
                put("speakerLineHe", "שלום וברוך הבא! אני מבין שהגעת בנוגע ל-$t. תוכל להסביר לי את הפרטים המדויקים כדי שאוכל לעזור לך מיד?")
                put("options", JSONArray().apply {
                    put(JSONObject().apply {
                        put("textEn", "Hi! Thank you for seeing me. Let me explain my situation clearly and what I'm looking to resolve today.")
                        put("textHe", "היי! תודה שקיבלת אותי. תן לי להסביר את המצב שלי בבירור ומה אני מבקש לפתור היום.")
                        put("nextNodeId", "step2")
                        put("feedbackHe", "✓ פתיחה מנומסת, רהוטה וברורה מאוד באנגלית!")
                        put("isOptimal", true)
                    })
                    put(JSONObject().apply {
                        put("textEn", "I am not know why I am standing in this place now.")
                        put("textHe", "אני לא יודע למה אני עומד במקום הזה עכשיו.")
                        put("nextNodeId", "start")
                        put("feedbackHe", "✗ שגיאת דקדוק כפולה! לא אומרים 'I am not know' אלא 'I do not know'.")
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
                put("speakerLineEn", "I see what you mean. We have two options available: a standard service that takes three days, or an express priority option. Which do you prefer?")
                put("speakerLineHe", "אני מבין למה אתה מתכוון. יש לנו שתי אפשרויות זמינות: שירות רגיל שלוקח שלושה ימים, או אפשרות אקספרס מהירה. מה אתה מעדיף?")
                put("options", JSONArray().apply {
                    put(JSONObject().apply {
                        put("textEn", "I'm on a tight schedule, so I'd prefer the express priority option. How much does it cost, and is there any extra fee?")
                        put("textHe", "אני בלוח זמנים צפוף, לכן אעדיף את אפשרות האקספרס המהירה. כמה זה עולה והאם יש עמלה נוספת?")
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
                    put(JSONObject().apply {
                        put("textEn", "Yes, I prefer yesterday morning.",
                        )
                        put("textHe", "כן, אני מעדיף את אתמול בבוקר.")
                        put("nextNodeId", "step2")
                        put("feedbackHe", "✗ תשובה חסרת היגיון שלא עונה לבחירה בין המסלולים.")
                        put("isOptimal", false)
                    })
                })
            })

            nodes.put(JSONObject().apply {
                put("id", "step3")
                put("speakerName", "Representative")
                put("speakerLineEn", "There is normally a $35 express fee, but since this is your first time with us, I can waive half of the fee if we complete the registration now.")
                put("speakerLineHe", "בדרך כלל יש עמלת אקספרס של 35 דולר, אך מאחר שזו הפעם הראשונה שלך אצלנו, אוכל לוותר על מחצית מהעמלה אם נשלים את הרישום כעת.")
                put("options", JSONArray().apply {
                    put(JSONObject().apply {
                        put("textEn", "I really appreciate that! What documents or identification do you need from me to finalize the process?")
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
                    put(JSONObject().apply {
                        put("textEn", "No, I want to pay triple price because I hate discounts.")
                        put("textHe", "לא, אני רוצה לשלם מחיר משולש כי אני שונא הנחות.")
                        put("nextNodeId", "step3")
                        put("feedbackHe", "✗ תשובה לא הגיונית בסיטואציה צרכנית.")
                        put("isOptimal", false)
                    })
                })
            })

            nodes.put(JSONObject().apply {
                put("id", "step4")
                put("speakerName", "Representative")
                put("speakerLineEn", "Just your passport and a local phone number or email address where we can send the confirmation receipt.")
                put("speakerLineHe", "רק את הדרכון שלך ומספר טלפון או כתובת אימייל שבהם נוכל לשלוח את קבלת האישור.")
                put("options", JSONArray().apply {
                    put(JSONObject().apply {
                        put("textEn", "Here is my passport, and let me write down my email address clearly so there are no typos.")
                        put("textHe", "הנה הדרכון שלי, ותן לי לרשום את כתובת האימייל שלי בבירור כדי שלא יהיו שגיאות הקלדה.")
                        put("nextNodeId", "step5")
                        put("feedbackHe", "✓ נהדר! 'Write down' (לרשום) ו-'typos' (שגיאות הקלדה) הם מונחים מעולים.")
                        put("isOptimal", true)
                    })
                    put(JSONObject().apply {
                        put("textEn", "My passport is swimming in the ocean right now.")
                        put("textHe", "הדרכון שלי שוחה באוקיינוס כרגע.")
                        put("nextNodeId", "step4")
                        put("feedbackHe", "✗ תשובה אבסורדית שלא מאפשרת להתקדם ברישום.")
                        put("isOptimal", false)
                    })
                })
            })

            nodes.put(JSONObject().apply {
                put("id", "step5")
                put("speakerName", "Representative")
                put("speakerLineEn", "Everything is entered into the system. Please review the summary on the screen and confirm if all the details are accurate.")
                put("speakerLineHe", "הכל הוזן למערכת. אנא עבור על הסיכום שעל המסך ואשר אם כל הפרטים מדויקים.")
                put("options", JSONArray().apply {
                    put(JSONObject().apply {
                        put("textEn", "I've double-checked the spelling of my name and contact info—everything looks completely accurate!")
                        put("textHe", "בדקתי פעמיים את איות השם ופרטי הקשר שלי — הכל נראה מדויק לחלוטין!")
                        put("nextNodeId", "ending")
                        put("feedbackHe", "✓ מושלם! 'Double-checked' הוא ביטוי מצוין לאימות פרטים.")
                        put("isOptimal", true)
                    })
                    put(JSONObject().apply {
                        put("textEn", "The screen is made of glass and plastic.")
                        put("textHe", "המסך עשוי מזכוכית ופלסטיק.")
                        put("nextNodeId", "step5")
                        put("feedbackHe", "✗ לא ענית לבקשה לאשר את נכונות הפרטים על המסך.")
                        put("isOptimal", false)
                    })
                })
            })

            nodes.put(JSONObject().apply {
                put("id", "ending")
                put("speakerName", "Representative")
                put("speakerLineEn", "All set! Your confirmation has been sent to your email. Thank you, and have a wonderful day!")
                put("speakerLineHe", "הכל מוכן! האישור שלך נשלח לאימייל. תודה רבה ושיהיה לך יום נפלא!")
                put("isEnding", true)
                put("options", JSONArray())
            })

            put("nodes", nodes)
        }
    }

    // 1. יצירת תרחיש שיחה שלם וארוך באמצעות ה-AI (עם הגנה מלאה מפני Timeout!)
    suspend fun generateOnlineAiScenario(topicPrompt: String, difficultyHe: String): Result<ConversationScenario> {
        return runCatching {
            val onlineAttempt = runCatching {
                val systemPrompt = """
                    Return ONLY raw valid JSON (no markdown) for a 5-stage English roleplay scenario for Hebrew speakers.
                    Nodes: "start", "step2", "step3", "step4", "ending".
                    Each non-ending node must have 3 options: 1 with "isOptimal": true ("nextNodeId" -> next step) and 2 with "isOptimal": false ("nextNodeId" -> same step, grammar/context trap).
                    Include Hebrew "feedbackHe" ("✓ ..." or "✗ ...").
                """.trimIndent()

                val userPrompt = """
                    Topic: "$topicPrompt", Difficulty: "$difficultyHe".
                    JSON format:
                    {"id":"ai_1","categoryHe":"שיחות AI ✨","titleHe":"...","titleEn":"...","difficultyHe":"$difficultyHe","nodes":[{"id":"start","speakerName":"...","speakerLineEn":"...","speakerLineHe":"...","isEnding":false,"options":[{"textEn":"...","textHe":"...","nextNodeId":"step2","feedbackHe":"✓ ...","isOptimal":true},{"textEn":"...","textHe":"...","nextNodeId":"start","feedbackHe":"✗ ...","isOptimal":false}]},{"id":"ending","speakerName":"...","speakerLineEn":"...","speakerLineHe":"...","isEnding":true,"options":[]}]}
                """.trimIndent()

                val responseText = requestAiJson(systemPrompt, userPrompt)
                val jsonObj = extractCleanJsonObject(responseText)
                jsonObj.put("id", "ai_${System.currentTimeMillis()}")
                jsonObj.put("categoryHe", "שיחות AI ✨")
                if (jsonObj.optString("titleHe").isBlank()) {
                    jsonObj.put("titleHe", topicPrompt)
                }
                jsonObj
            }

            // אם שרת ה-AI החיצוני החזיר תשובה בזמן - נשתמש בה; אם היה Timeout - נפעיל מיד את המנוע החכם המובנה!
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

    // 2. הוספת אפשרויות תשובה חדשות לשלב קיים בשיחה בזמן אמת (עם גיבוי מיידי ללא Timeout)
    suspend fun generateExtraAiOptionsForNode(
        node: DialogueNode,
        nextTargetNodeId: String
    ): Result<List<DialogueOption>> {
        return runCatching {
            val onlineAttempt = runCatching {
                val systemPrompt = "Return ONLY a valid JSON array of 2 English response options for Hebrew speakers: 1 optimal (true) and 1 grammar mistake trap (false)."
                val userPrompt = """
                    Speaker (${node.speakerName}) says: "${node.speakerLineEn}"
                    JSON array format:
                    [{"textEn":"...","textHe":"...","nextNodeId":"$nextTargetNodeId","feedbackHe":"✓ ...","isOptimal":true},{"textEn":"...","textHe":"...","nextNodeId":"${node.id}","feedbackHe":"✗ ...","isOptimal":false}]
                """.trimIndent()

                val responseText = requestAiJson(systemPrompt, userPrompt)
                val arr = extractCleanJsonArray(responseText)
                val list = mutableListOf<DialogueOption>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val isOpt = o.optBoolean("isOptimal", false)
                    list.add(
                        DialogueOption(
                            textEn = o.optString("textEn").trim(),
                            textHe = o.optString("textHe").trim(),
                            nextNodeId = if (isOpt) nextTargetNodeId else node.id,
                            feedbackHe = o.optString("feedbackHe").trim(),
                            isOptimal = isOpt
                        )
                    )
                }
                if (list.isEmpty()) throw IllegalStateException("Empty options")
                list
            }

            onlineAttempt.getOrElse {
                // גיבוי מיידי שמוסיף תשובות חכמות ומסיחים דקדוקיים בלי להיכשל ב-Timeout
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
