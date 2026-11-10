package com.example.englishtutor

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

enum class AbilityIconType {
    ROOT_SEED,
    VOCAB_EXPANSION,
    DIALOGUE_BRANCH,
    DIFFICULTY_BOOST,
    SPEED_MASTERY,
    BOSS_CROWN
}

enum class TrophyVisualType {
    FIRST_SPARK,
    BRONZE_SHIELD,
    SILVER_STAR,
    GOLD_CROWN,
    SPEED_LIGHTNING,
    MISSION_CHAMPION,
    TREE_CONQUEROR,
    DIAMOND_LEGEND
}

data class SkillTreeNodeData(
    val id: String,
    val parentId: String?,
    val isRoot: Boolean,
    val level: CefrLevel,
    val titleHe: String,
    val subtitleHe: String,
    val unlockRewardHe: String,
    val costPoints: Int,
    val iconType: AbilityIconType,
    val unlocksScenarioCategory: String? = null,
    val unlocksDifficultyHe: String? = null
)

data class TrophyMilestone(
    val id: String,
    val titleHe: String,
    val subtitleHe: String,
    val requiredPoints: Int,
    val visualType: TrophyVisualType,
    val isSpecialCondition: Boolean = false
)

class LevelProgressionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("level_progression_v1", Context.MODE_PRIVATE)

    var totalPoints by mutableIntStateOf(prefs.getInt("total_xp_points", 0))
        private set

    var highestPointsRecord by mutableIntStateOf(prefs.getInt("highest_xp_record", 0))
        private set

    var treeVersion by mutableIntStateOf(0)
        private set

    // עץ היכולות המובנה: שורשים (Roots) וענפים מתפצלים (Branches) שפותחים תוכן חדש
    val allTreeNodes: List<SkillTreeNodeData> = listOf(
        // --- שורש 1: A1 ---
        SkillTreeNodeData(
            id = "root_a1",
            parentId = null,
            isRoot = true,
            level = CefrLevel.A1,
            titleHe = "שורש 1: יסודות השפה (A1)",
            subtitleHe = "פותח את מאגר המילים הבסיסי והיכרות ראשונית",
            unlockRewardHe = "פתוח מההתחלה: מאגר מילים A1 ושיחות בסיס",
            costPoints = 0,
            iconType = AbilityIconType.ROOT_SEED
        ),
        SkillTreeNodeData(
            id = "branch_a1_vocab",
            parentId = "root_a1",
            isRoot = false,
            level = CefrLevel.A1,
            titleHe = "ענף: הרחבת אוצר מילים יומיומי",
            subtitleHe = "מוסיף מילים חדשות של התמצאות, אוכל וזמנים",
            unlockRewardHe = "פותח +40 מילים חדשות בקטגוריית A1",
            costPoints = 40,
            iconType = AbilityIconType.VOCAB_EXPANSION
        ),
        SkillTreeNodeData(
            id = "branch_a1_chat",
            parentId = "root_a1",
            isRoot = false,
            level = CefrLevel.A1,
            titleHe = "ענף: שיחות תחבורה ומסעדות",
            subtitleHe = "פותח סימולציות שיחה של הזמנת אוכל ורכבת",
            unlockRewardHe = "פותח שיחות 'אוכל ומסעדות' ו'תחבורה'",
            costPoints = 60,
            iconType = AbilityIconType.DIALOGUE_BRANCH,
            unlocksScenarioCategory = "אוכל ומסעדות"
        ),

        // --- שורש 2: A2 ---
        SkillTreeNodeData(
            id = "root_a2",
            parentId = "root_a1",
            isRoot = true,
            level = CefrLevel.A2,
            titleHe = "שורש 2: נוסע עצמאי (A2)",
            subtitleHe = "פתיחת רמת הקושי A2 בכרטיסיות ובמבחני האיות",
            unlockRewardHe = "פותח את כל מילים A2 ורמת קושי בסיסית-מתקדמת",
            costPoints = 100,
            iconType = AbilityIconType.ROOT_SEED,
            unlocksDifficultyHe = "קל-בינוני"
        ),
        SkillTreeNodeData(
            id = "branch_a2_airport",
            parentId = "root_a2",
            isRoot = false,
            level = CefrLevel.A2,
            titleHe = "ענף: טיסות, דרכונים ובתי מלון",
            subtitleHe = "פותח תרחישי שדה תעופה, מכס וצ'ק-אין במלון",
            unlockRewardHe = "פותח שיחות 'טיסות וחו״ל' ומילים חדשות",
            costPoints = 140,
            iconType = AbilityIconType.DIALOGUE_BRANCH,
            unlocksScenarioCategory = "טיסות וחו״ל"
        ),

        // --- שורש 3: B1 ---
        SkillTreeNodeData(
            id = "root_b1",
            parentId = "root_a2",
            isRoot = true,
            level = CefrLevel.B1,
            titleHe = "שורש 3: אנגלית שוטפת (B1)",
            subtitleHe = "פותח את מאגר המילים המרכזי ורמת קושי בינונית",
            unlockRewardHe = "פותח מילים ברמת B1 ובונוס מהירות באתגר 60 השניות",
            costPoints = 200,
            iconType = AbilityIconType.ROOT_SEED,
            unlocksDifficultyHe = "בינוני"
        ),
        SkillTreeNodeData(
            id = "branch_b1_clinic",
            parentId = "root_b1",
            isRoot = false,
            level = CefrLevel.B1,
            titleHe = "ענף: רפואה, קניות והחזרי מס",
            subtitleHe = "פותח שיחות מרפאה, בית מרקחת ורכישת אלקטרוניקה",
            unlockRewardHe = "פותח שיחות 'חירום ובריאות' ו'קניות ושירותים'",
            costPoints = 260,
            iconType = AbilityIconType.DIFFICULTY_BOOST,
            unlocksScenarioCategory = "חירום ובריאות"
        ),

        // --- שורש 4: B2 ---
        SkillTreeNodeData(
            id = "root_b2",
            parentId = "root_b1",
            isRoot = true,
            level = CefrLevel.B2,
            titleHe = "שורש 4: אנגלית עסקית וטכנולוגית (B2)",
            subtitleHe = "פתיחת מילים מתקדמות לעבודה, משא ומתן ונדל״ן",
            unlockRewardHe = "פותח את כל מילים B2 ורמת קושי 'בינוני-מתקדם'",
            costPoints = 350,
            iconType = AbilityIconType.ROOT_SEED,
            unlocksDifficultyHe = "בינוני-מתקדם"
        ),
        SkillTreeNodeData(
            id = "branch_b2_hightech",
            parentId = "root_b2",
            isRoot = false,
            level = CefrLevel.B2,
            titleHe = "ענף: ראיונות עבודה בהייטק ונדל״ן",
            subtitleHe = "פותח סימולציות ראיון פיתוח תוכנה וחוזה שכירות",
            unlockRewardHe = "פותח שיחות 'קריירה והייטק' ו'מגורים ונדל״ן'",
            costPoints = 450,
            iconType = AbilityIconType.BOSS_CROWN,
            unlocksScenarioCategory = "קריירה והייטק"
        ),

        // --- שורש 5: C1 ---
        SkillTreeNodeData(
            id = "root_c1",
            parentId = "root_b2",
            isRoot = true,
            level = CefrLevel.C1,
            titleHe = "שורש 5: שליטה אקדמית וניבים (C1)",
            subtitleHe = "פותח אוצר מילים אקדמי ומבחני דקדוק מורכבים",
            unlockRewardHe = "פותח את כל מילים C1 ורמת קושי 'מתקדם'",
            costPoints = 600,
            iconType = AbilityIconType.ROOT_SEED,
            unlocksDifficultyHe = "מתקדם"
        ),

        // --- שורש 6: C2 ---
        SkillTreeNodeData(
            id = "root_c2",
            parentId = "root_c1",
            isRoot = true,
            level = CefrLevel.C2,
            titleHe = "כתר השליטה העליון: מומחה שפה (C2)",
            subtitleHe = "פתיחת הרמה העליונה ביותר באפליקציה וכל קרבות הבוס",
            unlockRewardHe = "פותח את כל מאגר C2, רמת 'מתקדם מאוד' וגביע שליט העץ",
            costPoints = 850,
            iconType = AbilityIconType.BOSS_CROWN,
            unlocksDifficultyHe = "מתקדם מאוד"
        )
    )

    // רשימת כל הגביעים ושבירות השיאים בנקודות
    val allTrophies: List<TrophyMilestone> = listOf(
        TrophyMilestone(
            id = "trophy_100",
            titleHe = "ניצוץ הלמידה הראשון",
            subtitleHe = "שבירת שיא של 100 נקודות מצטברות באפליקציה",
            requiredPoints = 100,
            visualType = TrophyVisualType.FIRST_SPARK
        ),
        TrophyMilestone(
            id = "trophy_250",
            titleHe = "מגן הארד של השפה",
            subtitleHe = "שבירת שיא של 250 נקודות ופתיחת שורשים ראשונים",
            requiredPoints = 250,
            visualType = TrophyVisualType.BRONZE_SHIELD
        ),
        TrophyMilestone(
            id = "trophy_500",
            titleHe = "כוכב הכסף המתקדם",
            subtitleHe = "שבירת שיא של 500 נקודות שליטה באנגלית",
            requiredPoints = 500,
            visualType = TrophyVisualType.SILVER_STAR
        ),
        TrophyMilestone(
            id = "trophy_1000",
            titleHe = "כתר הזהב של ה-1,000",
            subtitleHe = "חציית רף 1,000 הנקודות באפליקציה!",
            requiredPoints = 1000,
            visualType = TrophyVisualType.GOLD_CROWN
        ),
        TrophyMilestone(
            id = "trophy_speed_100",
            titleHe = "ברק המהירות",
            subtitleHe = "שבירת שיא של 80+ נקודות במשחק בודד של אתגר 60 השניות",
            requiredPoints = 80,
            visualType = TrophyVisualType.SPEED_LIGHTNING,
            isSpecialCondition = true
        ),
        TrophyMilestone(
            id = "trophy_missions_5",
            titleHe = "אלוף המשימות היומיות",
            subtitleHe = "השלמת 3 משימות יומיות פרוצדורליות",
            requiredPoints = 150,
            visualType = TrophyVisualType.MISSION_CHAMPION,
            isSpecialCondition = true
        ),
        TrophyMilestone(
            id = "trophy_tree_master",
            titleHe = "שליט עץ היכולות",
            subtitleHe = "פתיחת 6 יכולות ושורשים בעץ היכולות",
            requiredPoints = 400,
            visualType = TrophyVisualType.TREE_CONQUEROR,
            isSpecialCondition = true
        ),
        TrophyMilestone(
            id = "trophy_2000",
            titleHe = "יהלום המאסטר האגדי",
            subtitleHe = "שבירת שיא על של 2,000 נקודות מצטברות!",
            requiredPoints = 2000,
            visualType = TrophyVisualType.DIAMOND_LEGEND
        )
    )

    init {
        // שורש A1 תמיד פתוח כברירת מחדל
        if (!prefs.getBoolean("node_unlocked_root_a1", false)) {
            prefs.edit().putBoolean("node_unlocked_root_a1", true).apply()
        }
    }

    fun isNodeUnlocked(nodeId: String): Boolean {
        if (nodeId == "root_a1") return true
        return prefs.getBoolean("node_unlocked_$nodeId", false)
    }

    fun canUnlockNode(node: SkillTreeNodeData): Boolean {
        if (isNodeUnlocked(node.id)) return false
        val parentUnlocked = node.parentId == null || isNodeUnlocked(node.parentId)
        return parentUnlocked && totalPoints >= node.costPoints
    }

    // פתיחת שורש או ענף בעץ באמצעות הנקודות שנצברו
    fun unlockNode(node: SkillTreeNodeData): Boolean {
        if (!canUnlockNode(node)) return false
        totalPoints = (totalPoints - node.costPoints).coerceAtLeast(0)
        prefs.edit()
            .putInt("total_xp_points", totalPoints)
            .putBoolean("node_unlocked_${node.id}", true)
            .apply()
        treeVersion++
        return true
    }

    // הוספת נקודות ושמירת שיא מצטבר עבור הגביעים
    fun addPoints(amount: Int) {
        if (amount <= 0) return
        totalPoints += amount
        val newCumulative = prefs.getInt("cumulative_xp_earned", 0) + amount
        if (newCumulative > highestPointsRecord) {
            highestPointsRecord = newCumulative
        }
        prefs.edit()
            .putInt("total_xp_points", totalPoints)
            .putInt("cumulative_xp_earned", newCumulative)
            .putInt("highest_xp_record", highestPointsRecord)
            .apply()
        treeVersion++
    }

    fun getUnlockedCefrLevels(): Set<CefrLevel> {
        val unlocked = mutableSetOf(CefrLevel.A1)
        for (node in allTreeNodes) {
            if (node.isRoot && isNodeUnlocked(node.id)) {
                unlocked.add(node.level)
            }
        }
        return unlocked
    }

    fun getUnlockedNodesCount(): Int {
        return allTreeNodes.count { isNodeUnlocked(it.id) }
    }

    fun isTrophyUnlocked(trophy: TrophyMilestone, speedHighScore: Int, completedMissionsCount: Int): Boolean {
        return when (trophy.id) {
            "trophy_speed_100" -> speedHighScore >= 80
            "trophy_missions_5" -> completedMissionsCount >= 3
            "trophy_tree_master" -> getUnlockedNodesCount() >= 6
            else -> highestPointsRecord >= trophy.requiredPoints
        }
    }
}
