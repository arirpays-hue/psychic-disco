package com.example.englishtutor

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

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
    val nodes: Map<String, DialogueNode>
)

class ConversationRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("conversations_prefs", Context.MODE_PRIVATE)

    fun loadScenarios(): List<ConversationScenario> {
        val list = mutableListOf<ConversationScenario>()
        try {
            val jsonStr = context.assets.open("conversations.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonStr)

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val categoryHe = obj.optString("categoryHe")
                val titleHe = obj.optString("titleHe")
                val titleEn = obj.optString("titleEn")
                val difficultyHe = obj.optString("difficultyHe", "בינוני")

                val nodesMap = LinkedHashMap<String, DialogueNode>()
                val nodesArr = obj.optJSONArray("nodes") ?: JSONArray()

                for (j in 0 until nodesArr.length()) {
                    val nObj = nodesArr.optJSONObject(j) ?: continue
                    val nodeId = nObj.optString("id")
                    val speakerName = nObj.optString("speakerName")
                    val speakerLineEn = nObj.optString("speakerLineEn")
                    val speakerLineHe = nObj.optString("speakerLineHe")
                    val isEnding = nObj.optBoolean("isEnding", false)

                    val optionsList = mutableListOf<DialogueOption>()
                    val optsArr = nObj.optJSONArray("options") ?: JSONArray()
                    for (k in 0 until optsArr.length()) {
                        val oObj = optsArr.optJSONObject(k) ?: continue
                        optionsList.add(
                            DialogueOption(
                                textEn = oObj.optString("textEn"),
                                textHe = oObj.optString("textHe"),
                                nextNodeId = oObj.optString("nextNodeId"),
                                feedbackHe = oObj.optString("feedbackHe"),
                                isOptimal = oObj.optBoolean("isOptimal", true)
                            )
                        )
                    }

                    nodesMap[nodeId] = DialogueNode(
                        id = nodeId,
                        speakerName = speakerName,
                        speakerLineEn = speakerLineEn,
                        speakerLineHe = speakerLineHe,
                        options = optionsList,
                        isEnding = isEnding
                    )
                }

                if (id.isNotEmpty() && nodesMap.isNotEmpty()) {
                    list.add(
                        ConversationScenario(
                            id = id,
                            categoryHe = categoryHe,
                            titleHe = titleHe,
                            titleEn = titleEn,
                            difficultyHe = difficultyHe,
                            nodes = nodesMap
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun isScenarioCompleted(scenarioId: String): Boolean =
        prefs.getBoolean("completed_$scenarioId", false)

    fun markScenarioCompleted(scenarioId: String) {
        prefs.edit().putBoolean("completed_$scenarioId", true).apply()
    }
}
