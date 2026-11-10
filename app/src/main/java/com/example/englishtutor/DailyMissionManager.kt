package com.example.englishtutor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

enum class MissionActionType {
    LEARN_WORDS,
    COMPLETE_ROLEPLAY,
    SPEED_SCORE,
    SPELLING_CORRECT
}

data class DailyMission(
    val id: String,
    val titleHe: String,
    val descriptionHe: String,
    val actionType: MissionActionType,
    val targetCount: Int,
    val currentCount: Int,
    val rewardPoints: Int,
    val isCompleted: Boolean
)

class DailyMissionManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("procedural_daily_missions_v1", Context.MODE_PRIVATE)

    var missionVersion by mutableIntStateOf(0)
        private set

    private fun todayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    // טוען או מייצר פרוצדורלית 3 משימות יומיות לפי התאריך (עובד ב-100% אופליין!)
    fun getTodayMissions(): List<DailyMission> {
        val today = todayDateKey()
        val savedDate = prefs.getString("last_mission_date", "")

        if (savedDate != today) {
            generateProceduralOfflineMissionsForToday(today)
        }

        val list = mutableListOf<DailyMission>()
        for (i in 0..2) {
            val id = prefs.getString("m_${i}_id", "m_$i") ?: "m_$i"
            val title = prefs.getString("m_${i}_title", "משימה יומית ${i + 1}") ?: ""
            val desc = prefs.getString("m_${i}_desc", "") ?: ""
            val typeName = prefs.getString("m_${i}_type", MissionActionType.LEARN_WORDS.name) ?: MissionActionType.LEARN_WORDS.name
            val target = prefs.getInt("m_${i}_target", 5)
            val current = prefs.getInt("m_${i}_current", 0)
            val reward = prefs.getInt("m_${i}_reward", 50)
            val done = prefs.getBoolean("m_${i}_done", false)

            list.add(
                DailyMission(
                    id = id,
                    titleHe = title,
                    descriptionHe = desc,
                    actionType = runCatching { MissionActionType.valueOf(typeName) }.getOrDefault(MissionActionType.LEARN_WORDS),
                    targetCount = target,
                    currentCount = current,
                    rewardPoints = reward,
                    isCompleted = done
                )
            )
        }
        return list
    }

    private fun generateProceduralOfflineMissionsForToday(todayKey: String) {
        val seed = todayKey.hashCode().toLong()
        val rng = Random(seed)

        val wordTargets = listOf(5, 8, 10, 12)
        val speedTargets = listOf(40, 60, 80, 100)
        val spellingTargets = listOf(3, 5, 7)

        val proceduralTitles1 = listOf(
            "צייד המילים היומי",
            "בונה אוצר המילים",
            "אלוף הזיכרון המהיר",
            "שומר השורשים"
        )
        val proceduralTitles2 = listOf(
            "מאסטר הדיאלוג החי",
            "שגריר השיחה באנגלית",
            "דיפלומט ללא טעויות",
            "כובש הסימולציות"
        )
        val proceduralTitles3 = listOf(
            "סופת הברק של 60 השניות",
            "אלוף האיות המדויק",
            "קצב הניאון המהיר",
            "מפצח הקוד הלשוני"
        )

        val wTarget = wordTargets.random(rng)
        val useSpeedForThird = rng.nextBoolean()
        val thirdTarget = if (useSpeedForThird) speedTargets.random(rng) else spellingTargets.random(rng)

        prefs.edit().apply {
            putString("last_mission_date", todayKey)

            // משימה 1: כרטיסיות מילים
            putString("m_0_id", "${todayKey}_0")
            putString("m_0_title", proceduralTitles1.random(rng))
            putString("m_0_desc", "סמן $wTarget מילים כ'נלמד' בכרטיסיות החכמות")
            putString("m_0_type", MissionActionType.LEARN_WORDS.name)
            putInt("m_0_target", wTarget)
            putInt("m_0_current", 0)
            putInt("m_0_reward", 60)
            putBoolean("m_0_done", false)

            // משימה 2: סימולציית שיחה או איות
            putString("m_1_id", "${todayKey}_1")
            putString("m_1_title", proceduralTitles2.random(rng))
            putString("m_1_desc", "השלם בהצלחה סימולציית שיחה או 4 מילים במבחן האיות")
            putString("m_1_type", MissionActionType.COMPLETE_ROLEPLAY.name)
            putInt("m_1_target", 1)
            putInt("m_1_current", 0)
            putInt("m_1_reward", 75)
            putBoolean("m_1_done", false)

            // משימה 3: אתגר 60 שניות או איות
            putString("m_2_id", "${todayKey}_2")
            putString("m_2_title", proceduralTitles3.random(rng))
            if (useSpeedForThird) {
                putString("m_2_desc", "השג לפחות $thirdTarget נקודות במשחק אתגר 60 השניות")
                putString("m_2_type", MissionActionType.SPEED_SCORE.name)
            } else {
                putString("m_2_desc", "איית נכון $thirdTarget מילים במבחן האיות וההקלדה")
                putString("m_2_type", MissionActionType.SPELLING_CORRECT.name)
            }
            putInt("m_2_target", thirdTarget)
            putInt("m_2_current", 0)
            putInt("m_2_reward", 85)
            putBoolean("m_2_done", false)

            apply()
        }

        // שליחת התראה על משימות יומיות חדשות
        sendNotification(
            title = "משימות יומיות חדשות מוכנות!",
            body = "הושלמו עבורך 3 משימות חדשות להיום לפתיחת יכולות בעץ!"
        )
    }

    // ה-AI פועל בשקט ברקע בכל יום כדי להעשיר את שמות המשימות היומיות אם יש חיבור לאינטרנט
    suspend fun enhanceTodayMissionsWithAiInBackground() = withContext(Dispatchers.IO) {
        val today = todayDateKey()
        if (prefs.getBoolean("ai_enhanced_$today", false)) return@withContext

        runCatching {
            val url = URL("https://text.pollinations.ai/openai")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 8000
                readTimeout = 12000
                doOutput = true
            }

            val prompt = """
                Return ONLY a valid JSON array of 3 short, exciting Hebrew mission titles for an English learning RPG game today ($today).
                Example: ["מבצע שליטה לשונית", "אתגר הדיאלוג המלכותי", "סופת המילים המהירה"]
            """.trimIndent()

            val body = JSONObject().apply {
                put("model", "openai-fast")
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val raw = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val start = raw.indexOf('[')
            val end = raw.lastIndexOf(']')
            if (start != -1 && end > start) {
                val arr = JSONArray(raw.substring(start, end + 1))
                val editor = prefs.edit()
                for (i in 0 until arr.length().coerceAtMost(3)) {
                    val aiTitle = arr.optString(i).trim()
                    if (aiTitle.length in 4..35) {
                        editor.putString("m_${i}_title", aiTitle)
                    }
                }
                editor.putBoolean("ai_enhanced_$today", true)
                editor.apply()
                missionVersion++
            }
        }
    }

    // מעדכן התקדמות במשימות היומיות ומעניק נקודות + התראה כשהמשימה מושלמת!
    fun recordAction(
        actionType: MissionActionType,
        amount: Int = 1,
        progressionManager: LevelProgressionManager
    ) {
        getTodayMissions() // מוודא שהמשימות של היום טעונות
        val editor = prefs.edit()
        var anyUpdated = false

        for (i in 0..2) {
            val done = prefs.getBoolean("m_${i}_done", false)
            if (done) continue

            val typeStr = prefs.getString("m_${i}_type", "") ?: ""
            val matches = typeStr == actionType.name ||
                (typeStr == MissionActionType.COMPLETE_ROLEPLAY.name && actionType == MissionActionType.SPELLING_CORRECT)

            if (matches) {
                val target = prefs.getInt("m_${i}_target", 5)
                val prev = prefs.getInt("m_${i}_current", 0)
                val updated = if (actionType == MissionActionType.SPEED_SCORE) {
                    maxOf(prev, amount)
                } else {
                    (prev + amount).coerceAtMost(target)
                }

                editor.putInt("m_${i}_current", updated)
                anyUpdated = true

                if (updated >= target) {
                    val reward = prefs.getInt("m_${i}_reward", 60)
                    val title = prefs.getString("m_${i}_title", "משימה יומית") ?: "משימה יומית"
                    editor.putBoolean("m_${i}_done", true)
                    val totalDoneEver = prefs.getInt("total_missions_completed_ever", 0) + 1
                    editor.putInt("total_missions_completed_ever", totalDoneEver)

                    progressionManager.addPoints(reward)
                    sendNotification(
                        title = "משימה הושלמה: $title!",
                        body = "זכית ב-+$reward נקודות יכולת לפתיחת ענפים חדשים בעץ!"
                    )
                }
            }
        }

        if (anyUpdated) {
            editor.apply()
            missionVersion++
        }
    }

    fun getTotalCompletedMissionsEver(): Int = prefs.getInt("total_missions_completed_ever", 0)

    private fun sendNotification(title: String, body: String) {
        runCatching {
            val channelId = "daily_missions_channel"
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "משימות יומיות והישגים",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                manager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_neon)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
        }
    }
}
