package com.example.bible.data.housecare

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Приёмы пищи в разделе «Меню для семьи». */
enum class FamilyMealSlot(val key: String, val title: String, val timeHint: String) {
    BREAKFAST("breakfast", "Завтрак", "08:00"),
    LUNCH("lunch", "Обед", "13:00"),
    SNACK_KIDS("snack_kids", "Полдник и детям", "16:30"),
    DINNER("dinner", "Ужин", "19:00");

    companion object {
        fun fromKey(k: String): FamilyMealSlot =
            entries.firstOrNull { it.key == k } ?: LUNCH
    }
}

/** Профиль члена семьи с учётом детского меню и аллергенов. */
data class FamilyMemberProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relationLabel: String,
    val isChildMenu: Boolean = false,
    val allergens: List<String> = emptyList(),
    val portionFactor: Float = 1.0f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("relationLabel", relationLabel)
        put("isChildMenu", isChildMenu)
        put("portionFactor", portionFactor.toDouble())
        put("allergens", JSONArray(allergens))
    }

    companion object {
        fun fromJson(j: JSONObject): FamilyMemberProfile {
            val arr = j.optJSONArray("allergens") ?: JSONArray()
            val list = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            return FamilyMemberProfile(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = j.optString("name", ""),
                relationLabel = j.optString("relationLabel", ""),
                isChildMenu = j.optBoolean("isChildMenu", false),
                allergens = list,
                portionFactor = j.optDouble("portionFactor", 1.0).toFloat(),
            )
        }
    }
}

/** Ингредиент блюда для автоматического расчёта списка покупок. */
data class RecipeIngredient(
    val name: String,
    val amount: Float,
    val unit: String,
    val aisle: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("amount", amount.toDouble())
        put("unit", unit)
        put("aisle", aisle)
    }

    companion object {
        fun fromJson(j: JSONObject): RecipeIngredient = RecipeIngredient(
            name = j.optString("name", ""),
            amount = j.optDouble("amount", 1.0).toFloat(),
            unit = j.optString("unit", "шт."),
            aisle = j.optString("aisle", "grocery"),
        )
    }
}

/** Карточка домашнего рецепта с КБЖУ и детской адаптацией. */
data class FamilyRecipe(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val prepMinutes: Int,
    val cookMinutes: Int,
    val caloriesPerServing: Int,
    val proteinGrams: Int,
    val fatGrams: Int,
    val carbsGrams: Int,
    val baseServings: Int = 4,
    val isKidFriendly: Boolean = true,
    val isFasting: Boolean = false,
    val allergens: List<String> = emptyList(),
    val kidAdaptationNote: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("subtitle", subtitle)
        put("prepMinutes", prepMinutes)
        put("cookMinutes", cookMinutes)
        put("caloriesPerServing", caloriesPerServing)
        put("proteinGrams", proteinGrams)
        put("fatGrams", fatGrams)
        put("carbsGrams", carbsGrams)
        put("baseServings", baseServings)
        put("isKidFriendly", isKidFriendly)
        put("isFasting", isFasting)
        put("allergens", JSONArray(allergens))
        put("kidAdaptationNote", kidAdaptationNote)
        put("ingredients", JSONArray().apply { ingredients.forEach { put(it.toJson()) } })
        put("steps", JSONArray(steps))
    }

    companion object {
        fun fromJson(j: JSONObject): FamilyRecipe {
            val ingArr = j.optJSONArray("ingredients") ?: JSONArray()
            val stepArr = j.optJSONArray("steps") ?: JSONArray()
            val algArr = j.optJSONArray("allergens") ?: JSONArray()
            return FamilyRecipe(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = j.optString("title", ""),
                subtitle = j.optString("subtitle", ""),
                prepMinutes = j.optInt("prepMinutes", 10),
                cookMinutes = j.optInt("cookMinutes", 20),
                caloriesPerServing = j.optInt("caloriesPerServing", 250),
                proteinGrams = j.optInt("proteinGrams", 12),
                fatGrams = j.optInt("fatGrams", 8),
                carbsGrams = j.optInt("carbsGrams", 28),
                baseServings = j.optInt("baseServings", 4),
                isKidFriendly = j.optBoolean("isKidFriendly", true),
                isFasting = j.optBoolean("isFasting", false),
                allergens = (0 until algArr.length()).mapNotNull { algArr.optString(it) },
                kidAdaptationNote = j.optString("kidAdaptationNote", ""),
                ingredients = (0 until ingArr.length()).mapNotNull { idx ->
                    ingArr.optJSONObject(idx)?.let(RecipeIngredient::fromJson)
                },
                steps = (0 until stepArr.length()).mapNotNull { stepArr.optString(it) },
            )
        }
    }
}

/** Запись в расписании на день недели (1 = Пн .. 7 = Вс). */
data class PlannedMealEntry(
    val dayOfWeek: Int,
    val slotKey: String,
    val recipeId: String,
    val servings: Int = 4,
    val useSafeSubstitution: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("dayOfWeek", dayOfWeek)
        put("slotKey", slotKey)
        put("recipeId", recipeId)
        put("servings", servings)
        put("useSafeSubstitution", useSafeSubstitution)
    }

    companion object {
        fun fromJson(j: JSONObject): PlannedMealEntry = PlannedMealEntry(
            dayOfWeek = j.optInt("dayOfWeek", 1).coerceIn(1, 7),
            slotKey = j.optString("slotKey", "lunch"),
            recipeId = j.optString("recipeId", ""),
            servings = j.optInt("servings", 4).coerceAtLeast(1),
            useSafeSubstitution = j.optBoolean("useSafeSubstitution", true),
        )
    }
}
