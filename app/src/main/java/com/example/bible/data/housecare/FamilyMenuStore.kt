package com.example.bible.data.housecare

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.familyMenuDataStore: DataStore<Preferences> by preferencesDataStore(name = "family_menu")

private object FamilyMenuKeys {
    val DOCUMENT_JSON = stringPreferencesKey("family_menu_json")
}

data class FamilyMenuData(
    val members: List<FamilyMemberProfile> = emptyList(),
    val recipes: List<FamilyRecipe> = emptyList(),
    val schedule: List<PlannedMealEntry> = emptyList(),
    val checkedShoppingKeys: Set<String> = emptySet(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("members", JSONArray().apply { members.forEach { put(it.toJson()) } })
        put("recipes", JSONArray().apply { recipes.forEach { put(it.toJson()) } })
        put("schedule", JSONArray().apply { schedule.forEach { put(it.toJson()) } })
        put("checkedShoppingKeys", JSONArray(checkedShoppingKeys.toList()))
    }

    companion object {
        fun parse(raw: String?): FamilyMenuData {
            if (raw.isNullOrBlank()) return defaultSeed()
            return try {
                val j = JSONObject(raw)
                val memArr = j.optJSONArray("members") ?: JSONArray()
                val recArr = j.optJSONArray("recipes") ?: JSONArray()
                val schArr = j.optJSONArray("schedule") ?: JSONArray()
                val chkArr = j.optJSONArray("checkedShoppingKeys") ?: JSONArray()
                FamilyMenuData(
                    members = (0 until memArr.length()).mapNotNull { memArr.optJSONObject(it)?.let(FamilyMemberProfile::fromJson) },
                    recipes = (0 until recArr.length()).mapNotNull { recArr.optJSONObject(it)?.let(FamilyRecipe::fromJson) },
                    schedule = (0 until schArr.length()).mapNotNull { schArr.optJSONObject(it)?.let(PlannedMealEntry::fromJson) },
                    checkedShoppingKeys = (0 until chkArr.length()).mapNotNull { chkArr.optString(it) }.toSet(),
                )
            } catch (_: Exception) {
                defaultSeed()
            }
        }

        fun defaultSeed(): FamilyMenuData = FamilyMenuData(
            members = listOf(
                FamilyMemberProfile(name = "Алексей", relationLabel = "Глава семьи (Муж)", isChildMenu = false, allergens = emptyList()),
                FamilyMemberProfile(name = "Мария", relationLabel = "Хозяйка дома (Жена)", isChildMenu = false, allergens = listOf("honey")),
                FamilyMemberProfile(name = "Даниил", relationLabel = "Сын (школьник)", isChildMenu = true, allergens = listOf("nuts")),
                FamilyMemberProfile(name = "София", relationLabel = "Младшая дочка", isChildMenu = true, allergens = listOf("citrus", "spicy", "nuts")),
            ),
            recipes = listOf(
                FamilyRecipe(id = "rec-syrniki", title = "Домашние сырники в духовке с малиной", subtitle = "Нежные творожные сырники без лишнего масла и жарки", prepMinutes = 10, cookMinutes = 20, caloriesPerServing = 265, proteinGrams = 18, fatGrams = 9, carbsGrams = 27, isKidFriendly = true, isFasting = false, kidAdaptationNote = "Запекайте в силиконовых формочках без корочки."),
                FamilyRecipe(id = "rec-oatmeal-berries", title = "Томлёная овсяно-пшённая каша с печёным яблоком", subtitle = "Тёплый сытный семейный завтрак с корицей и маслом", prepMinutes = 5, cookMinutes = 15, caloriesPerServing = 230, proteinGrams = 7, fatGrams = 6, carbsGrams = 38, isKidFriendly = true, isFasting = false, kidAdaptationNote = "Яблоки для малышей натрите на крупной тёрке."),
                FamilyRecipe(id = "rec-chicken-soup", title = "Домашний куриный суп с тонкой вермишелью и укропом", subtitle = "Прозрачный золотистый бульон без зажарки", prepMinutes = 15, cookMinutes = 35, caloriesPerServing = 210, proteinGrams = 22, fatGrams = 7, carbsGrams = 16, isKidFriendly = true, isFasting = false, kidAdaptationNote = "Луковицу варите целиком и вынимайте перед закладкой лапши."),
                FamilyRecipe(id = "rec-turkey-meatballs", title = "Тефтели из индейки в сметанно-овощном соусе с пюре", subtitle = "Мягкие домашние ёжики с рисом и морковью, томлённые в духовке", prepMinutes = 15, cookMinutes = 30, caloriesPerServing = 365, proteinGrams = 29, fatGrams = 12, carbsGrams = 34, isKidFriendly = true, isFasting = false, kidAdaptationNote = "Готовятся без предварительного обжаривания."),
            ),
        )
    }
}

/** Хранилище раздела «Меню для семьи» в модуле «Попечительная о доме». */
class FamilyMenuStore(context: Context) {
    private val app = context.applicationContext

    val data: Flow<FamilyMenuData> = app.familyMenuDataStore.data.map { prefs ->
        FamilyMenuData.parse(prefs[FamilyMenuKeys.DOCUMENT_JSON])
    }

    suspend fun save(data: FamilyMenuData) {
        app.familyMenuDataStore.edit { prefs ->
            prefs[FamilyMenuKeys.DOCUMENT_JSON] = data.toJson().toString()
        }
    }

    suspend fun assignMeal(dayOfWeek: Int, slotKey: String, recipeId: String, servings: Int) {
        val cur = data.first()
        val filtered = cur.schedule.filterNot { it.dayOfWeek == dayOfWeek && it.slotKey == slotKey }
        val updated = filtered + PlannedMealEntry(dayOfWeek, slotKey, recipeId, servings)
        save(cur.copy(schedule = updated))
    }

    suspend fun toggleShoppingItem(key: String) {
        val cur = data.first()
        val next = if (key in cur.checkedShoppingKeys) cur.checkedShoppingKeys - key else cur.checkedShoppingKeys + key
        save(cur.copy(checkedShoppingKeys = next))
    }
}
