package com.example.bible.data.housecare

import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** 4 основных приёма пищи в расписании семьи. */
enum class FamilyMealSlot(
    val key: String,
    val title: String,
    val timeLabel: String,
    val fullLabel: String,
    val emoji: String,
) {
    BREAKFAST("breakfast", "Завтрак", "08:00", "Завтрак · 08:00", "☀️"),
    LUNCH("lunch", "Обед", "13:00", "Обед · 13:00", "🍲"),
    SNACK_KIDS("snack_kids", "Полдник и детям", "16:30", "Полдник и детям · 16:30", "🧸"),
    DINNER("dinner", "Ужин", "19:00", "Ужин · 19:00", "🕯️"),
    ;

    companion object {
        fun fromKey(key: String?): FamilyMealSlot =
            entries.firstOrNull { it.key == key } ?: BREAKFAST
    }
}

/** Пищевые аллергены и ограничения, учитываемые в семейном меню. */
enum class FamilyAllergen(
    val key: String,
    val title: String,
    val emoji: String,
    val defaultSubstitutionHint: String,
) {
    LACTOSE("lactose", "Лактоза", "🥛", "Заменить молоко на овсяное/кокосовое или воду, масло — на топлёное гхи или оливковое"),
    GLUTEN("gluten", "Глютен", "🌾", "Использовать гречневую, рисовую или кукурузную муку/крупу вместо пшеничной"),
    NUTS("nuts", "Орехи", "🥜", "Исключить орехи или заменить тыквенными/подсолнечными семечками"),
    EGGS("eggs", "Яйца", "🥚", "Заменить 1 яйцо на ½ спелого банана или 1 ст. л. льняной муки с 3 ст. л. воды"),
    HONEY("honey", "Мёд", "🍯", "Заменить мёд на печёное яблоко, финиковый сироп или щепотку тростникового сахара"),
    CITRUS("citrus", "Цитрусовые", "🍋", "Заменить лимонный сок на мягкий яблочный сок или веточку тимьяна"),
    FISH("fish", "Рыба", "🐟", "Заменить рыбу на филе индейки или нежную куриную грудку"),
    SPICY("spicy", "Острое", "🌶️", "Готовить со сладкой паприкой, укропом и базиликом без острого перца и чеснока"),
    ;

    companion object {
        fun fromKey(key: String?): FamilyAllergen? = entries.firstOrNull { it.key == key }
        fun labelOf(key: String): String = fromKey(key)?.let { "${it.emoji} ${it.title}" } ?: key
    }
}

/** Возрастная роль члена семьи и базовый коэффициент порции. */
enum class FamilyMemberRole(
    val key: String,
    val title: String,
    val emoji: String,
    val defaultPortionFactor: Float,
) {
    ADULT("adult", "Взрослый", "🧑", 1.0f),
    SCHOOL_KID("school_kid", "Школьник", "🎒", 0.8f),
    TODDLER("toddler", "Малыш", "👶", 0.5f),
    ;

    companion object {
        fun fromKey(key: String?): FamilyMemberRole =
            entries.firstOrNull { it.key == key } ?: ADULT
    }
}

/** Отделы магазина для удобной группировки списка покупок и кладовой. */
enum class StoreAisle(
    val key: String,
    val title: String,
    val emoji: String,
) {
    PRODUCE("produce", "Овощи, фрукты и зелень", "🥬"),
    DAIRY_EGGS("dairy_eggs", "Молочное, сыр и яйца", "🥛"),
    MEAT_FISH("meat_fish", "Мясо, птица и рыба", "🍗"),
    GROCERY("grocery", "Бакалея, крупы и масла", "🌾"),
    BAKERY_KIDS("bakery_kids", "Выпечка и детский стол", "🧁"),
    ;

    companion object {
        fun fromKey(key: String?): StoreAisle =
            entries.firstOrNull { it.key == key } ?: GROCERY
    }
}

/** Профиль члена семьи с его предпочтениями, детским столом и списком аллергенов. */
data class FamilyMemberProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relationLabel: String = "Семья",
    val role: FamilyMemberRole = FamilyMemberRole.ADULT,
    val isChildMenu: Boolean = role != FamilyMemberRole.ADULT,
    val allergens: List<String> = emptyList(),
    val portionFactor: Float = role.defaultPortionFactor,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("relationLabel", relationLabel)
        put("role", role.key)
        put("isChildMenu", isChildMenu)
        put("allergens", JSONArray().apply { allergens.forEach { put(it) } })
        put("portionFactor", portionFactor.toDouble())
    }

    companion object {
        fun fromJson(j: JSONObject): FamilyMemberProfile {
            val role = FamilyMemberRole.fromKey(j.optString("role", "adult"))
            return FamilyMemberProfile(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = j.optString("name", "Член семьи"),
                relationLabel = j.optString("relationLabel", role.title),
                role = role,
                isChildMenu = j.optBoolean("isChildMenu", role != FamilyMemberRole.ADULT),
                allergens = j.optJSONArray("allergens").let { arr ->
                    if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
                },
                portionFactor = j.optFloatOrNull("portionFactor")?.coerceIn(0.3f, 2.0f) ?: role.defaultPortionFactor,
            )
        }
    }
}

/** Ингредиент рецепта с количеством, единицей измерения и отделом магазина. */
data class RecipeIngredient(
    val name: String,
    val amount: Double,
    val unit: String,
    val aisle: String = StoreAisle.GROCERY.key,
) {
    fun scaledAmount(multiplier: Double): Double = amount * multiplier

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("amount", amount)
        put("unit", unit)
        put("aisle", aisle)
    }

    companion object {
        fun fromJson(j: JSONObject): RecipeIngredient = RecipeIngredient(
            name = j.optString("name", ""),
            amount = j.optDouble("amount", 1.0),
            unit = j.optString("unit", "шт"),
            aisle = j.optString("aisle", StoreAisle.GROCERY.key),
        )
    }
}

/** Пошаговая инструкция приготовления с опциональным временем для запуска таймера. */
data class CookingStep(
    val stepNumber: Int,
    val instruction: String,
    val timerMinutes: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("stepNumber", stepNumber)
        put("instruction", instruction)
        put("timerMinutes", timerMinutes)
    }

    companion object {
        fun fromJson(j: JSONObject): CookingStep = CookingStep(
            stepNumber = j.optInt("stepNumber", 1),
            instruction = j.optString("instruction", ""),
            timerMinutes = j.optInt("timerMinutes", 0).coerceAtLeast(0),
        )
    }
}

/** Карточка домашнего рецепта с КБЖУ, адаптацией для детей и безопасными заменами аллергенов. */
data class FamilyRecipe(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val slotKeys: List<String> = listOf(FamilyMealSlot.LUNCH.key),
    val prepMinutes: Int = 10,
    val cookMinutes: Int = 20,
    val caloriesPerServing: Int = 320,
    val proteinGrams: Int = 14,
    val fatGrams: Int = 11,
    val carbsGrams: Int = 38,
    val baseServings: Int = 4,
    val isKidFriendly: Boolean = true,
    val isFasting: Boolean = false,
    val allergens: List<String> = emptyList(),
    val kidAdaptationNote: String = "Готовить на умеренном огне без острых специй, подавать тёплым.",
    val safeSubstitutions: Map<String, String> = emptyMap(),
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<CookingStep> = emptyList(),
) {
    val totalMinutes: Int get() = prepMinutes + cookMinutes

    fun substitutionTextFor(allergenKeys: Collection<String>): String {
        val notes = allergenKeys.mapNotNull { key ->
            safeSubstitutions[key] ?: FamilyAllergen.fromKey(key)?.defaultSubstitutionHint
        }.distinct()
        return if (notes.isEmpty()) {
            "Замените чувствительный ингредиент на гипоаллергенный аналог."
        } else {
            notes.joinToString("; ")
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("subtitle", subtitle)
        put("slotKeys", JSONArray().apply { slotKeys.forEach { put(it) } })
        put("prepMinutes", prepMinutes)
        put("cookMinutes", cookMinutes)
        put("caloriesPerServing", caloriesPerServing)
        put("proteinGrams", proteinGrams)
        put("fatGrams", fatGrams)
        put("carbsGrams", carbsGrams)
        put("baseServings", baseServings)
        put("isKidFriendly", isKidFriendly)
        put("isFasting", isFasting)
        put("allergens", JSONArray().apply { allergens.forEach { put(it) } })
        put("kidAdaptationNote", kidAdaptationNote)
        val subsObj = JSONObject()
        safeSubstitutions.forEach { (k, v) -> subsObj.put(k, v) }
        put("safeSubstitutions", subsObj)
        put("ingredients", JSONArray().apply { ingredients.forEach { put(it.toJson()) } })
        put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(j: JSONObject): FamilyRecipe {
            val subsObj = j.optJSONObject("safeSubstitutions")
            val subsMap = mutableMapOf<String, String>()
            if (subsObj != null) {
                val keys = subsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    subsMap[k] = subsObj.optString(k, "")
                }
            }
            return FamilyRecipe(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = j.optString("title", "Домашнее блюдо"),
                subtitle = j.optString("subtitle", ""),
                slotKeys = j.optJSONArray("slotKeys").let { arr ->
                    if (arr == null || arr.length() == 0) {
                        listOf(FamilyMealSlot.LUNCH.key)
                    } else {
                        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
                    }
                },
                prepMinutes = j.optInt("prepMinutes", 10).coerceAtLeast(1),
                cookMinutes = j.optInt("cookMinutes", 15).coerceAtLeast(0),
                caloriesPerServing = j.optInt("caloriesPerServing", 300).coerceAtLeast(10),
                proteinGrams = j.optInt("proteinGrams", 12).coerceAtLeast(0),
                fatGrams = j.optInt("fatGrams", 10).coerceAtLeast(0),
                carbsGrams = j.optInt("carbsGrams", 35).coerceAtLeast(0),
                baseServings = j.optInt("baseServings", 4).coerceAtLeast(1),
                isKidFriendly = j.optBoolean("isKidFriendly", true),
                isFasting = j.optBoolean("isFasting", false),
                allergens = j.optJSONArray("allergens").let { arr ->
                    if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
                },
                kidAdaptationNote = j.optString("kidAdaptationNote", ""),
                safeSubstitutions = subsMap,
                ingredients = j.optJSONArray("ingredients").let { arr ->
                    if (arr == null) emptyList() else (0 until arr.length()).map { RecipeIngredient.fromJson(arr.getJSONObject(it)) }
                },
                steps = j.optJSONArray("steps").let { arr ->
                    if (arr == null) emptyList() else (0 until arr.length()).map { CookingStep.fromJson(arr.getJSONObject(it)) }
                },
            )
        }
    }
}

/** Запланированный приём пищи на конкретный день недели (1 = Пн .. 7 = Вс) и слот. */
data class PlannedMealEntry(
    val id: String = UUID.randomUUID().toString(),
    val dayOfWeek: Int,
    val slotKey: String,
    val recipeId: String,
    val servings: Int = 4,
    val useKidAdaptation: Boolean = true,
    val useSafeSubstitution: Boolean = false,
    val isCooked: Boolean = false,
) {
    val slot: FamilyMealSlot get() = FamilyMealSlot.fromKey(slotKey)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("dayOfWeek", dayOfWeek.coerceIn(1, 7))
        put("slotKey", slotKey)
        put("recipeId", recipeId)
        put("servings", servings.coerceIn(1, 20))
        put("useKidAdaptation", useKidAdaptation)
        put("useSafeSubstitution", useSafeSubstitution)
        put("isCooked", isCooked)
    }

    companion object {
        fun fromJson(j: JSONObject): PlannedMealEntry = PlannedMealEntry(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            dayOfWeek = j.optInt("dayOfWeek", 1).coerceIn(1, 7),
            slotKey = j.optString("slotKey", FamilyMealSlot.BREAKFAST.key),
            recipeId = j.optString("recipeId", ""),
            servings = j.optInt("servings", 4).coerceIn(1, 20),
            useKidAdaptation = j.optBoolean("useKidAdaptation", true),
            useSafeSubstitution = j.optBoolean("useSafeSubstitution", false),
            isCooked = j.optBoolean("isCooked", false),
        )
    }
}

/** Готовый недельный семейный рацион на 7 дней. */
data class ReadyMealKit(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val emoji: String,
    val avgDailyCalories: Int,
    val avgDailyCookMinutes: Int,
    val estimatedWeeklyBudgetRub: Int,
    val isKidFocused: Boolean,
    val entries: List<PlannedMealEntry>,
)

/** Продукт в домашней кладовой («Что уже есть дома»). */
data class PantryItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Double,
    val unit: String,
    val aisle: String = StoreAisle.GROCERY.key,
) {
    fun normalizedKey(): String = "${name.trim().lowercase(Locale.ROOT)}|${unit.trim().lowercase(Locale.ROOT)}"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("amount", amount)
        put("unit", unit)
        put("aisle", aisle)
    }

    companion object {
        fun fromJson(j: JSONObject): PantryItem = PantryItem(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = j.optString("name", ""),
            amount = j.optDouble("amount", 0.0).coerceAtLeast(0.0),
            unit = j.optString("unit", "шт"),
            aisle = j.optString("aisle", StoreAisle.GROCERY.key),
        )
    }
}

/** Элемент списка покупок (автоматически агрегированный из меню или добавленный вручную). */
data class ShoppingItem(
    val id: String = UUID.randomUUID().toString(),
    val key: String = id,
    val name: String,
    val requiredAmount: Double,
    val inPantryAmount: Double = 0.0,
    val toBuyAmount: Double = (requiredAmount - inPantryAmount).coerceAtLeast(0.0),
    val unit: String,
    val aisle: String = StoreAisle.GROCERY.key,
    val checked: Boolean = false,
    val isCustom: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("key", key)
        put("name", name)
        put("requiredAmount", requiredAmount)
        put("inPantryAmount", inPantryAmount)
        put("toBuyAmount", toBuyAmount)
        put("unit", unit)
        put("aisle", aisle)
        put("checked", checked)
        put("isCustom", isCustom)
    }

    companion object {
        fun fromJson(j: JSONObject): ShoppingItem {
            val req = j.optDouble("requiredAmount", 1.0)
            val pan = j.optDouble("inPantryAmount", 0.0)
            return ShoppingItem(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                key = j.optString("key").ifBlank { UUID.randomUUID().toString() },
                name = j.optString("name", ""),
                requiredAmount = req,
                inPantryAmount = pan,
                toBuyAmount = j.optDouble("toBuyAmount", (req - pan).coerceAtLeast(0.0)),
                unit = j.optString("unit", "шт"),
                aisle = j.optString("aisle", StoreAisle.GROCERY.key),
                checked = j.optBoolean("checked", false),
                isCustom = j.optBoolean("isCustom", true),
            )
        }
    }
}

/** Сводка КБЖУ и времени готовки на один день недели. */
data class DayMenuSummary(
    val dayOfWeek: Int,
    val totalCalories: Int,
    val totalProtein: Int,
    val totalFat: Int,
    val totalCarbs: Int,
    val totalCookMinutes: Int,
    val plannedSlotsCount: Int,
    val cookedSlotsCount: Int,
)

/** Конфликт аллергенов для конкретного запланированного блюда и членов семьи. */
data class MealAllergenWarning(
    val allergenKeys: List<String>,
    val affectedMemberNames: List<String>,
    val substitutionAdvice: String,
)

/** Полное состояние подраздела «Меню для семьи». */
data class FamilyMenuData(
    val members: List<FamilyMemberProfile> = emptyList(),
    val recipes: List<FamilyRecipe> = emptyList(),
    val weekPlan: List<PlannedMealEntry> = emptyList(),
    val pantryItems: List<PantryItem> = emptyList(),
    val customShoppingItems: List<ShoppingItem> = emptyList(),
    val checkedShoppingKeys: Set<String> = emptySet(),
    val activeKitId: String = "kit_classic_family",
    val orderedKitId: String? = null,
    val syncHabitsWithMenu: Boolean = true,
) {
    fun recipeById(id: String): FamilyRecipe? = recipes.firstOrNull { it.id == id }

    /** Рекомендуемое число порций с учётом всех членов семьи и их коэффициентов. */
    val recommendedServings: Int
        get() = ceil(members.sumOf { it.portionFactor.toDouble() }.coerceAtLeast(1.0)).toInt().coerceIn(1, 16)

    /** Все уникальные аллергены, отмеченные хотя бы у одного члена семьи. */
    val familyAllergenKeys: Set<String>
        get() = members.flatMap { it.allergens }.toSet()

    /** Есть ли в семье дети или включён ли у кого-то «Детский щадящий стол». */
    val hasChildrenInFamily: Boolean
        get() = members.any { it.isChildMenu || it.role != FamilyMemberRole.ADULT }

    fun mealsForDay(dayOfWeek: Int): List<PlannedMealEntry> {
        val day = dayOfWeek.coerceIn(1, 7)
        val bySlot = weekPlan.filter { it.dayOfWeek == day }.associateBy { it.slotKey }
        return FamilyMealSlot.entries.mapNotNull { bySlot[it.key] }
    }

    fun mealFor(dayOfWeek: Int, slot: FamilyMealSlot): PlannedMealEntry? =
        weekPlan.firstOrNull { it.dayOfWeek == dayOfWeek && it.slotKey == slot.key }

    fun daySummary(dayOfWeek: Int): DayMenuSummary {
        val entries = mealsForDay(dayOfWeek)
        var kcal = 0
        var prot = 0
        var fat = 0
        var carbs = 0
        var minutes = 0
        var cooked = 0
        entries.forEach { entry ->
            val r = recipeById(entry.recipeId) ?: return@forEach
            kcal += r.caloriesPerServing
            prot += r.proteinGrams
            fat += r.fatGrams
            carbs += r.carbsGrams
            minutes += r.totalMinutes
            if (entry.isCooked) cooked++
        }
        return DayMenuSummary(
            dayOfWeek = dayOfWeek,
            totalCalories = kcal,
            totalProtein = prot,
            totalFat = fat,
            totalCarbs = carbs,
            totalCookMinutes = minutes,
            plannedSlotsCount = entries.size,
            cookedSlotsCount = cooked,
        )
    }

    /** Проверяет, содержит ли блюдо аллергены кого-либо из членов семьи. */
    fun checkAllergenWarning(recipe: FamilyRecipe): MealAllergenWarning? {
        if (recipe.allergens.isEmpty() || members.isEmpty()) return null
        val matchedAllergens = recipe.allergens.filter { it in familyAllergenKeys }
        if (matchedAllergens.isEmpty()) return null
        val affectedNames = members
            .filter { m -> m.allergens.any { it in matchedAllergens } }
            .map { it.name }
        return MealAllergenWarning(
            allergenKeys = matchedAllergens,
            affectedMemberNames = affectedNames,
            substitutionAdvice = recipe.substitutionTextFor(matchedAllergens),
        )
    }

    /**
     * Формирует единый список покупок на неделю:
     * 1) суммирует одинаковые ингредиенты из всех запланированных блюд с учётом `servings / baseServings`;
     * 2) автоматически вычитает количество, которое уже есть в «Домашней кладовой»;
     * 3) объединяет с пользовательскими покупками.
     */
    fun buildWeeklyShoppingList(): List<ShoppingItem> {
        data class Acc(
            val name: String,
            val unit: String,
            val aisle: String,
            var required: Double = 0.0,
        )

        val map = linkedMapOf<String, Acc>()
        weekPlan.forEach { entry ->
            val recipe = recipeById(entry.recipeId) ?: return@forEach
            val factor = entry.servings.toDouble() / recipe.baseServings.coerceAtLeast(1).toDouble()
            recipe.ingredients.forEach { ing ->
                val normKey = "${ing.name.trim().lowercase(Locale.ROOT)}|${ing.unit.trim().lowercase(Locale.ROOT)}"
                val existing = map.getOrPut(normKey) {
                    Acc(name = ing.name.trim(), unit = ing.unit.trim(), aisle = ing.aisle)
                }
                existing.required += ing.scaledAmount(factor)
            }
        }

        val pantryByKey = pantryItems.groupBy { it.normalizedKey() }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val autoItems = map.entries.mapNotNull { (normKey, acc) ->
            val inPantry = pantryByKey[normKey] ?: 0.0
            val toBuy = (acc.required - inPantry).coerceAtLeast(0.0)
            // Показываем позицию, если нужно докупить или если она частично перекрыта кладовой
            if (acc.required <= 0.01) return@mapNotNull null
            val itemKey = "auto:$normKey"
            ShoppingItem(
                id = itemKey,
                key = itemKey,
                name = acc.name,
                requiredAmount = roundAmount(acc.required),
                inPantryAmount = roundAmount(inPantry),
                toBuyAmount = roundAmount(toBuy),
                unit = acc.unit,
                aisle = acc.aisle,
                checked = itemKey in checkedShoppingKeys || toBuy <= 0.001,
                isCustom = false,
            )
        }

        val manualItems = customShoppingItems.map { item ->
            item.copy(checked = item.key in checkedShoppingKeys || item.checked)
        }

        return (autoItems + manualItems).sortedWith(
            compareBy(
                { StoreAisle.fromKey(it.aisle).ordinal },
                { it.checked },
                { it.name.lowercase(Locale.ROOT) },
            ),
        )
    }

    private fun roundAmount(value: Double): Double =
        if (value >= 10.0) value.roundToInt().toDouble() else (value * 10.0).roundToInt() / 10.0

    fun toJson(): JSONObject = JSONObject().apply {
        put("members", JSONArray().apply { members.forEach { put(it.toJson()) } })
        put("recipes", JSONArray().apply { recipes.forEach { put(it.toJson()) } })
        put("weekPlan", JSONArray().apply { weekPlan.forEach { put(it.toJson()) } })
        put("pantryItems", JSONArray().apply { pantryItems.forEach { put(it.toJson()) } })
        put("customShoppingItems", JSONArray().apply { customShoppingItems.forEach { put(it.toJson()) } })
        put("checkedShoppingKeys", JSONArray().apply { checkedShoppingKeys.forEach { put(it) } })
        put("activeKitId", activeKitId)
        put("orderedKitId", orderedKitId ?: JSONObject.NULL)
        put("syncHabitsWithMenu", syncHabitsWithMenu)
    }

    companion object {
        fun parse(json: String?): FamilyMenuData {
            if (json.isNullOrBlank()) return FamilyMenuDefaults.initialData()
            return try {
                val root = JSONObject(json)
                val members = root.optJSONArray("members").let { arr ->
                    if (arr == null || arr.length() == 0) {
                        FamilyMenuDefaults.defaultMembers()
                    } else {
                        (0 until arr.length()).map { FamilyMemberProfile.fromJson(arr.getJSONObject(it)) }
                    }
                }
                val parsedRecipes = root.optJSONArray("recipes").let { arr ->
                    if (arr == null || arr.length() == 0) {
                        FamilyMenuDefaults.defaultRecipes()
                    } else {
                        (0 until arr.length()).map { FamilyRecipe.fromJson(arr.getJSONObject(it)) }
                    }
                }
                // Гарантируем, что базовые рецепты всегда доступны для готовых рационов
                val existingIds = parsedRecipes.map { it.id }.toSet()
                val mergedRecipes = parsedRecipes + FamilyMenuDefaults.defaultRecipes().filterNot { it.id in existingIds }

                val weekPlan = root.optJSONArray("weekPlan").let { arr ->
                    if (arr == null || arr.length() == 0) {
                        FamilyMenuDefaults.defaultWeekPlan()
                    } else {
                        (0 until arr.length()).map { PlannedMealEntry.fromJson(arr.getJSONObject(it)) }
                    }
                }
                val pantry = root.optJSONArray("pantryItems").let { arr ->
                    if (arr == null) {
                        FamilyMenuDefaults.defaultPantry()
                    } else {
                        (0 until arr.length()).map { PantryItem.fromJson(arr.getJSONObject(it)) }
                    }
                }
                val customShop = root.optJSONArray("customShoppingItems").let { arr ->
                    if (arr == null) emptyList() else (0 until arr.length()).map { ShoppingItem.fromJson(arr.getJSONObject(it)) }
                }
                val checkedKeys = root.optJSONArray("checkedShoppingKeys").let { arr ->
                    if (arr == null) emptySet() else (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }.toSet()
                }
                FamilyMenuData(
                    members = members,
                    recipes = mergedRecipes,
                    weekPlan = weekPlan,
                    pantryItems = pantry,
                    customShoppingItems = customShop,
                    checkedShoppingKeys = checkedKeys,
                    activeKitId = root.optString("activeKitId", "kit_classic_family"),
                    orderedKitId = root.optStringOrNull("orderedKitId"),
                    syncHabitsWithMenu = root.optBoolean("syncHabitsWithMenu", true),
                )
            } catch (_: Exception) {
                FamilyMenuDefaults.initialData()
            }
        }
    }
}

internal fun formatIngredientAmount(value: Double, unit: String): String {
    val clean = if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
    }
    return "$clean $unit"
}

/** Библиотека проверенных домашних рецептов, профилей семьи и готовых недельных рационов. */
object FamilyMenuDefaults {

    fun initialData(): FamilyMenuData = FamilyMenuData(
        members = defaultMembers(),
        recipes = defaultRecipes(),
        weekPlan = defaultWeekPlan(),
        pantryItems = defaultPantry(),
        customShoppingItems = emptyList(),
        checkedShoppingKeys = emptySet(),
        activeKitId = "kit_classic_family",
        orderedKitId = null,
        syncHabitsWithMenu = true,
    )

    fun defaultMembers(): List<FamilyMemberProfile> = listOf(
        FamilyMemberProfile(
            id = "member_mom",
            name = "Мама (Хозяйка)",
            relationLabel = "Мама",
            role = FamilyMemberRole.ADULT,
            isChildMenu = false,
            allergens = emptyList(),
            portionFactor = 1.0f,
        ),
        FamilyMemberProfile(
            id = "member_dad",
            name = "Папа (Глава семьи)",
            relationLabel = "Папа",
            role = FamilyMemberRole.ADULT,
            isChildMenu = false,
            allergens = emptyList(),
            portionFactor = 1.2f,
        ),
        FamilyMemberProfile(
            id = "member_school",
            name = "Сын / Дочка (Школьник)",
            relationLabel = "Ребёнок",
            role = FamilyMemberRole.SCHOOL_KID,
            isChildMenu = true,
            allergens = listOf(FamilyAllergen.SPICY.key),
            portionFactor = 0.8f,
        ),
        FamilyMemberProfile(
            id = "member_toddler",
            name = "Малыш",
            relationLabel = "Малыш",
            role = FamilyMemberRole.TODDLER,
            isChildMenu = true,
            allergens = listOf(FamilyAllergen.HONEY.key, FamilyAllergen.NUTS.key, FamilyAllergen.SPICY.key),
            portionFactor = 0.5f,
        ),
    )

    fun defaultPantry(): List<PantryItem> = listOf(
        PantryItem(id = "pan_oats", name = "Овсяные хлопья", amount = 300.0, unit = "г", aisle = StoreAisle.GROCERY.key),
        PantryItem(id = "pan_buckwheat", name = "Гречневая крупа", amount = 400.0, unit = "г", aisle = StoreAisle.GROCERY.key),
        PantryItem(id = "pan_rice", name = "Рис круглозёрный", amount = 300.0, unit = "г", aisle = StoreAisle.GROCERY.key),
        PantryItem(id = "pan_potato", name = "Картофель", amount = 800.0, unit = "г", aisle = StoreAisle.PRODUCE.key),
        PantryItem(id = "pan_carrot", name = "Морковь", amount = 300.0, unit = "г", aisle = StoreAisle.PRODUCE.key),
        PantryItem(id = "pan_eggs", name = "Яйца куриные", amount = 6.0, unit = "шт", aisle = StoreAisle.DAIRY_EGGS.key),
    )

    fun defaultRecipes(): List<FamilyRecipe> = listOf(
        // ЗАВТРАКИ
        FamilyRecipe(
            id = "rec_oatmeal_apple",
            title = "Молочная овсяная каша с томлёным яблоком и корицей",
            subtitle = "Нежный тёплый завтрак для всей семьи без лишнего сахара",
            slotKeys = listOf(FamilyMealSlot.BREAKFAST.key),
            prepMinutes = 5,
            cookMinutes = 12,
            caloriesPerServing = 285,
            proteinGrams = 9,
            fatGrams = 8,
            carbsGrams = 44,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = false,
            allergens = listOf(FamilyAllergen.LACTOSE.key, FamilyAllergen.GLUTEN.key),
            kidAdaptationNote = "Для малыша натрите яблоко на мелкой тёрке и потушите прямо в каше 3 минуты до мягкости.",
            safeSubstitutions = mapOf(
                FamilyAllergen.LACTOSE.key to "Сварить на воде с овсяным или кокосовым молоком 1:1 и каплей оливкового масла",
                FamilyAllergen.GLUTEN.key to "Взять безглютеновые овсяные хлопья или пшённые хлопья",
            ),
            ingredients = listOf(
                RecipeIngredient("Овсяные хлопья", 240.0, "г", StoreAisle.GROCERY.key),
                RecipeIngredient("Молоко 2.5%", 500.0, "мл", StoreAisle.DAIRY_EGGS.key),
                RecipeIngredient("Яблоки сладкие", 2.0, "шт", StoreAisle.PRODUCE.key),
                RecipeIngredient("Масло сливочное", 30.0, "г", StoreAisle.DAIRY_EGGS.key),
            ),
            steps = listOf(
                CookingStep(1, "Вскипятите воду с молоком в кастрюле с толстым дном, всыпьте овсяные хлопья.", 3),
                CookingStep(2, "Добавьте мелко нарезанное яблоко и томите на слабом огне, помешивая.", 8),
                CookingStep(3, "Добавьте кусочек сливочного масла, накройте крышкой и дайте настояться.", 3),
            ),
        ),
        FamilyRecipe(
            id = "rec_baked_syrniki",
            title = "Пышные творожные сырники из духовки (без жарки)",
            subtitle = "Золотистые сырники с ванилью и сметаной — щадящий вариант для детей",
            slotKeys = listOf(FamilyMealSlot.BREAKFAST.key, FamilyMealSlot.SNACK_KIDS.key),
            prepMinutes = 10,
            cookMinutes = 20,
            caloriesPerServing = 310,
            proteinGrams = 18,
            fatGrams = 11,
            carbsGrams = 32,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = false,
            allergens = listOf(FamilyAllergen.LACTOSE.key, FamilyAllergen.EGGS.key, FamilyAllergen.GLUTEN.key),
            kidAdaptationNote = "Выпекание в духовке на пергаменте даёт нежную корочку без канцерогенов и лишнего масла.",
            safeSubstitutions = mapOf(
                FamilyAllergen.GLUTEN.key to "Заменить пшеничную муку на рисовую муку или кукурузный крахмал",
                FamilyAllergen.EGGS.key to "Вместо яйца добавить ½ размятого спелого банана",
            ),
            ingredients = listOf(
                RecipeIngredient("Творог 5%", 500.0, "г", StoreAisle.DAIRY_EGGS.key),
                RecipeIngredient("Яйца куриные", 1.0, "шт", StoreAisle.DAIRY_EGGS.key),
                RecipeIngredient("Мука рисовая или пшеничная", 60.0, "г", StoreAisle.GROCERY.key),
                RecipeIngredient("Сметана 15%", 100.0, "г", StoreAisle.DAIRY_EGGS.key),
            ),
            steps = listOf(
                CookingStep(1, "Разомните творог вилкой с яйцом и мукой до однородной мягкой массы.", 5),
                CookingStep(2, "Сформируйте аккуратные шайбочки и выложите на противень с пергаментом.", 5),
                CookingStep(3, "Выпекайте при 180 °C до нежного румянца.", 20),
            ),
        ),
        FamilyRecipe(
            id = "rec_millet_pumpkin",
            title = "Солнечная пшённая каша с тыквой (Постная / Семейная)",
            subtitle = "Богатая витаминами каша с медовой тыквой без глютена",
            slotKeys = listOf(FamilyMealSlot.BREAKFAST.key),
            prepMinutes = 7,
            cookMinutes = 20,
            caloriesPerServing = 265,
            proteinGrams = 7,
            fatGrams = 6,
            carbsGrams = 47,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = true,
            allergens = listOf(FamilyAllergen.HONEY.key),
            kidAdaptationNote = "Для малышей до 3 лет не добавляйте мёд — сладкая запечённая тыква сама даст приятную сладость.",
            safeSubstitutions = mapOf(
                FamilyAllergen.HONEY.key to "Исключить мёд, добавить немного сладкого яблочного пюре",
            ),
            ingredients = listOf(
                RecipeIngredient("Пшено шлифованное", 220.0, "г", StoreAisle.GROCERY.key),
                RecipeIngredient("Тыква сладкая", 350.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Мёд цветочный", 20.0, "г", StoreAisle.GROCERY.key),
            ),
            steps = listOf(
                CookingStep(1, "Промойте пшено тёплой водой и обдайте кипятком, чтобы убрать горчинку.", 3),
                CookingStep(2, "Нарежьте тыкву мелкими кубиками, залейте водой и варите вместе с пшеном под крышкой.", 20),
            ),
        ),
        FamilyRecipe(
            id = "rec_steam_omelet",
            title = "Воздушный паровой омлет с брокколи и сыром",
            subtitle = "Белковый завтрак за 15 минут, тающий во рту",
            slotKeys = listOf(FamilyMealSlot.BREAKFAST.key),
            prepMinutes = 5,
            cookMinutes = 10,
            caloriesPerServing = 240,
            proteinGrams = 16,
            fatGrams = 15,
            carbsGrams = 6,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = false,
            allergens = listOf(FamilyAllergen.EGGS.key, FamilyAllergen.LACTOSE.key),
            kidAdaptationNote = "Разберите брокколи на крошечные соцветия — в суфле дети едят её с удовольствием.",
            safeSubstitutions = mapOf(
                FamilyAllergen.LACTOSE.key to "Использовать безлактозное молоко и исключить сыр",
            ),
            ingredients = listOf(
                RecipeIngredient("Яйца куриные", 6.0, "шт", StoreAisle.DAIRY_EGGS.key),
                RecipeIngredient("Молоко 2.5%", 150.0, "мл", StoreAisle.DAIRY_EGGS.key),
                RecipeIngredient("Брокколи свежая или с/м", 200.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Сыр твёрдый", 60.0, "г", StoreAisle.DAIRY_EGGS.key),
            ),
            steps = listOf(
                CookingStep(1, "Перемешайте яйца с молоком венчиком без взбивания в пену.", 3),
                CookingStep(2, "Добавьте мелкие соцветия брокколи и готовьте под крышкой на слабом огне или на пару.", 10),
            ),
        ),

        // ОБЕДЫ
        FamilyRecipe(
            id = "rec_turkey_meatball_soup",
            title = "Домашний супчик с фрикадельками из индейки и вермишелью",
            subtitle = "Любимый прозрачный бульон с овощами и нежными фрикадельками",
            slotKeys = listOf(FamilyMealSlot.LUNCH.key),
            prepMinutes = 12,
            cookMinutes = 20,
            caloriesPerServing = 295,
            proteinGrams = 22,
            fatGrams = 9,
            carbsGrams = 31,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = false,
            allergens = listOf(FamilyAllergen.GLUTEN.key),
            kidAdaptationNote = "Не обжаривайте морковь и лук на сковороде — пассеруйте их в капле воды с маслом или кладите сразу в бульон.",
            safeSubstitutions = mapOf(
                FamilyAllergen.GLUTEN.key to "Заменить пшеничную вермишель на горсть риса или безглютеновую паутинку",
            ),
            ingredients = listOf(
                RecipeIngredient("Фарш из филе индейки", 400.0, "г", StoreAisle.MEAT_FISH.key),
                RecipeIngredient("Картофель", 400.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Морковь", 120.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Вермишель мелкая", 80.0, "г", StoreAisle.GROCERY.key),
                RecipeIngredient("Укроп свежий", 20.0, "г", StoreAisle.PRODUCE.key),
            ),
            steps = listOf(
                CookingStep(1, "Нарежьте картофель кубиками, морковь натрите на тёрке и поставьте вариться в 1.8 л воды.", 10),
                CookingStep(2, "Скатайте маленькие фрикадельки из индейки и аккуратно опустите в кипящий суп.", 7),
                CookingStep(3, "Всыпьте мелкую вермишель и свежий укроп, проварите до готовности.", 5),
            ),
        ),
        FamilyRecipe(
            id = "rec_cream_pumpkin_lentil_soup",
            title = "Бархатный крем-суп из красной чечевицы и моркови (Постный)",
            subtitle = "Сытный согревающий суп-пюре с растительным белком за 20 минут",
            slotKeys = listOf(FamilyMealSlot.LUNCH.key),
            prepMinutes = 7,
            cookMinutes = 18,
            caloriesPerServing = 275,
            proteinGrams = 14,
            fatGrams = 7,
            carbsGrams = 39,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = true,
            allergens = emptyList(),
            kidAdaptationNote = "Суп полностью гипоаллергенен, подавайте с домашними сухариками.",
            safeSubstitutions = emptyMap(),
            ingredients = listOf(
                RecipeIngredient("Чечевица красная", 250.0, "г", StoreAisle.GROCERY.key),
                RecipeIngredient("Морковь", 200.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Картофель", 250.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Масло оливковое", 25.0, "мл", StoreAisle.GROCERY.key),
            ),
            steps = listOf(
                CookingStep(1, "Промойте красную чечевицу, нарежьте картофель и морковь.", 5),
                CookingStep(2, "Варите овощи с чечевицей на умеренном огне до мягкости.", 15),
                CookingStep(3, "Пюрируйте блендером с ложкой оливкового масла.", 3),
            ),
        ),
        FamilyRecipe(
            id = "rec_borscht_gentle",
            title = "Домашний щадящий борщ со сметаной и говядиной",
            subtitle = "Ароматный семейный обед с запечённой свёклой без уксуса и зажарки",
            slotKeys = listOf(FamilyMealSlot.LUNCH.key),
            prepMinutes = 15,
            cookMinutes = 35,
            caloriesPerServing = 340,
            proteinGrams = 21,
            fatGrams = 13,
            carbsGrams = 34,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = false,
            allergens = listOf(FamilyAllergen.LACTOSE.key),
            kidAdaptationNote = "Тушение свёклы с ложкой сладкого яблочного пюре вместо уксуса сохраняет яркий цвет и бережёт детский желудок.",
            safeSubstitutions = mapOf(
                FamilyAllergen.LACTOSE.key to "Подавать без сметаны, заправив зеленью и каплей ароматного масла",
            ),
            ingredients = listOf(
                RecipeIngredient("Говядина на бульон", 450.0, "г", StoreAisle.MEAT_FISH.key),
                RecipeIngredient("Свёкла столовая", 300.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Капуста белокочанная", 300.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Картофель", 350.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Сметана 15%", 100.0, "г", StoreAisle.DAIRY_EGGS.key),
            ),
            steps = listOf(
                CookingStep(1, "В бульон с отварной говядиной добавьте картофель и нашинкованную капусту.", 15),
                CookingStep(2, "Потушите тёртую свёклу с морковью в черпаке бульона под крышкой.", 12),
                CookingStep(3, "Соедините овощи в кастрюле, прогрейте на тихом огне и дайте настояться.", 8),
            ),
        ),

        // ПОЛДНИК И ДЕТСКИЙ СТОЛ
        FamilyRecipe(
            id = "rec_cottage_souffle",
            title = "Нежное творожно-банановое суфле в формочках",
            subtitle = "Воздушный полдник для малышей и школьников без рафинированного сахара",
            slotKeys = listOf(FamilyMealSlot.SNACK_KIDS.key, FamilyMealSlot.BREAKFAST.key),
            prepMinutes = 7,
            cookMinutes = 15,
            caloriesPerServing = 210,
            proteinGrams = 13,
            fatGrams = 7,
            carbsGrams = 24,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = false,
            allergens = listOf(FamilyAllergen.LACTOSE.key, FamilyAllergen.EGGS.key),
            kidAdaptationNote = "Подходит малышам с 1 года: спелый банан полностью заменяет сахар.",
            safeSubstitutions = mapOf(
                FamilyAllergen.EGGS.key to "Готовить как нежный мусс без запекания, взбив творог с печёным яблоком",
            ),
            ingredients = listOf(
                RecipeIngredient("Творог детский мягкий", 400.0, "г", StoreAisle.DAIRY_EGGS.key),
                RecipeIngredient("Бананы спелые", 2.0, "шт", StoreAisle.PRODUCE.key),
                RecipeIngredient("Яйца куриные", 1.0, "шт", StoreAisle.DAIRY_EGGS.key),
            ),
            steps = listOf(
                CookingStep(1, "Взбейте блендером творог, бананы и яйцо до кремовой пышности.", 4),
                CookingStep(2, "Разложите по керамическим формочкам и запекайте при 180 °C.", 15),
            ),
        ),
        FamilyRecipe(
            id = "rec_baked_apples_oats",
            title = "Запечённые яблоки с овсяной крошкой и изюмом (Постные)",
            subtitle = "Тёплый ароматный десерт для уютного семейного чаепития",
            slotKeys = listOf(FamilyMealSlot.SNACK_KIDS.key),
            prepMinutes = 8,
            cookMinutes = 18,
            caloriesPerServing = 195,
            proteinGrams = 3,
            fatGrams = 4,
            carbsGrams = 38,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = true,
            allergens = listOf(FamilyAllergen.GLUTEN.key, FamilyAllergen.NUTS.key),
            kidAdaptationNote = "Для малыша подавайте мягкую запечённую мякоть яблока ложечкой без орехов.",
            safeSubstitutions = mapOf(
                FamilyAllergen.NUTS.key to "Исключить орехи, оставить только изюм и овсяные хлопья",
                FamilyAllergen.GLUTEN.key to "Заменить овсяные хлопья на кунжут и семечки подсолнечника",
            ),
            ingredients = listOf(
                RecipeIngredient("Яблоки крупные", 4.0, "шт", StoreAisle.PRODUCE.key),
                RecipeIngredient("Изюм светлый", 60.0, "г", StoreAisle.GROCERY.key),
                RecipeIngredient("Овсяные хлопья", 50.0, "г", StoreAisle.GROCERY.key),
                RecipeIngredient("Грецкие орехи", 40.0, "г", StoreAisle.GROCERY.key),
            ),
            steps = listOf(
                CookingStep(1, "Выньте сердцевину яблок, оставив донышко, и наполните промытым изюмом с овсяными хлопьями.", 6),
                CookingStep(2, "Налейте на дно формы немного воды и запекайте до мягкости яблок.", 18),
            ),
        ),
        FamilyRecipe(
            id = "rec_berry_kissel_cookies",
            title = "Домашний ягодный кисель с овсяным печеньем",
            subtitle = "Витаминный полдник, обволакивающий и бережный для пищеварения",
            slotKeys = listOf(FamilyMealSlot.SNACK_KIDS.key),
            prepMinutes = 5,
            cookMinutes = 10,
            caloriesPerServing = 180,
            proteinGrams = 2,
            fatGrams = 3,
            carbsGrams = 36,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = true,
            allergens = listOf(FamilyAllergen.GLUTEN.key),
            kidAdaptationNote = "Ягоды протираем в самом конце в тёплый отвар, чтобы сохранить витамин С.",
            safeSubstitutions = mapOf(
                FamilyAllergen.GLUTEN.key to "Подавать с домашними рисовыми хлебцами вместо овсяного печенья",
            ),
            ingredients = listOf(
                RecipeIngredient("Ягоды замороженные (клюква/черника)", 250.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Крахмал картофельный", 35.0, "г", StoreAisle.GROCERY.key),
                RecipeIngredient("Печенье детское овсяное", 120.0, "г", StoreAisle.BAKERY_KIDS.key),
            ),
            steps = listOf(
                CookingStep(1, "Проварите ягоды в 1 л воды 5 минут, процедите и тонкой струйкой введите разведённый крахмал.", 8),
            ),
        ),

        // УЖИНЫ
        FamilyRecipe(
            id = "rec_chicken_Lazy_hedgehogs",
            title = "Нежные «ёжики» из куриного филе с рисом в сметанном соусе",
            subtitle = "Сытный семейный ужин в одной глубокой сковороде или сотейнике",
            slotKeys = listOf(FamilyMealSlot.DINNER.key, FamilyMealSlot.LUNCH.key),
            prepMinutes = 12,
            cookMinutes = 25,
            caloriesPerServing = 355,
            proteinGrams = 28,
            fatGrams = 12,
            carbsGrams = 33,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = false,
            allergens = listOf(FamilyAllergen.LACTOSE.key, FamilyAllergen.EGGS.key),
            kidAdaptationNote = "Тефтельки тушатся в соусе без обжаривания и получаются очень мягкими для детских зубок.",
            safeSubstitutions = mapOf(
                FamilyAllergen.LACTOSE.key to "Потушить в томатно-морковном овощном бульоне без сметаны",
                FamilyAllergen.EGGS.key to "Фарш с полуотварным рисом отлично держит форму без добавления яйца",
            ),
            ingredients = listOf(
                RecipeIngredient("Филе куриной грудки или бедра", 500.0, "г", StoreAisle.MEAT_FISH.key),
                RecipeIngredient("Рис круглозёрный", 150.0, "г", StoreAisle.GROCERY.key),
                RecipeIngredient("Морковь", 120.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Сметана 15%", 120.0, "г", StoreAisle.DAIRY_EGGS.key),
            ),
            steps = listOf(
                CookingStep(1, "Отварите рис до полуготовности и смешайте с куриным фаршем.", 8),
                CookingStep(2, "Сформируйте тефтельки, выложите в сотейник с тёртой морковью.", 5),
                CookingStep(3, "Залейте тёплой водой со сметаной и томите под крышкой на медленном огне.", 22),
            ),
        ),
        FamilyRecipe(
            id = "rec_baked_cod_potato",
            title = "Филе белой рыбы, запечённое с молодым картофелем и укропом",
            subtitle = "Лёгкий и полезный ужин в фольге или форме без брызг на плите",
            slotKeys = listOf(FamilyMealSlot.DINNER.key),
            prepMinutes = 10,
            cookMinutes = 25,
            caloriesPerServing = 320,
            proteinGrams = 26,
            fatGrams = 8,
            carbsGrams = 35,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = false,
            allergens = listOf(FamilyAllergen.FISH.key, FamilyAllergen.CITRUS.key),
            kidAdaptationNote = "Используйте филе трески или минтая без косточек, перед подачей проверьте кусочки для малыша.",
            safeSubstitutions = mapOf(
                FamilyAllergen.FISH.key to "Заменить филе рыбы на медальоны из индейки",
                FamilyAllergen.CITRUS.key to "Готовить без лимонного сока, только с укропом и оливковым маслом",
            ),
            ingredients = listOf(
                RecipeIngredient("Филе трески или минтая", 550.0, "г", StoreAisle.MEAT_FISH.key),
                RecipeIngredient("Картофель", 600.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Укроп свежий", 25.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Масло оливковое", 25.0, "мл", StoreAisle.GROCERY.key),
            ),
            steps = listOf(
                CookingStep(1, "Нарежьте картофель тонкими кружочками и выложите в форму с оливковым маслом.", 8),
                CookingStep(2, "Сверху разложите кусочки рыбного филе и веточки укропа, накройте фольгой.", 4),
                CookingStep(3, "Запекайте в духовке при 190 °C до мягкости картофеля.", 25),
            ),
        ),
        FamilyRecipe(
            id = "rec_buckwheat_mushrooms",
            title = "Гречка по-купечески с шампиньонами и томлёными овощами (Постная)",
            subtitle = "Быстрый ароматный ужин за 20 минут, богатый железом и клетчаткой",
            slotKeys = listOf(FamilyMealSlot.DINNER.key, FamilyMealSlot.LUNCH.key),
            prepMinutes = 6,
            cookMinutes = 16,
            caloriesPerServing = 290,
            proteinGrams = 11,
            fatGrams = 7,
            carbsGrams = 48,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = true,
            allergens = emptyList(),
            kidAdaptationNote = "Для малышей вместо грибов отложите порцию гречки с томлёной морковкой и кабачком.",
            safeSubstitutions = emptyMap(),
            ingredients = listOf(
                RecipeIngredient("Гречневая крупа", 280.0, "г", StoreAisle.GROCERY.key),
                RecipeIngredient("Шампиньоны свежие", 250.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Морковь", 150.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Масло подсолнечное", 20.0, "мл", StoreAisle.GROCERY.key),
            ),
            steps = listOf(
                CookingStep(1, "Припустите нарезанные шампиньоны и морковь в сотейнике с ложкой воды и масла.", 5),
                CookingStep(2, "Всыпьте промытую гречку, залейте горячей водой 1:2 и томите под крышкой.", 15),
            ),
        ),
        FamilyRecipe(
            id = "rec_turkey_pasta_veggies",
            title = "Быстрая паста с кусочками индейки и сладкими томатами",
            subtitle = "Выручалочка для занятого буднего вечера за 18 минут",
            slotKeys = listOf(FamilyMealSlot.DINNER.key),
            prepMinutes = 6,
            cookMinutes = 12,
            caloriesPerServing = 365,
            proteinGrams = 25,
            fatGrams = 9,
            carbsGrams = 46,
            baseServings = 4,
            isKidFriendly = true,
            isFasting = false,
            allergens = listOf(FamilyAllergen.GLUTEN.key),
            kidAdaptationNote = "Макароны-бантики или спиральки из твёрдых сортов пшеницы очень нравятся детям.",
            safeSubstitutions = mapOf(
                FamilyAllergen.GLUTEN.key to "Использовать кукурузные или гречневые макароны",
            ),
            ingredients = listOf(
                RecipeIngredient("Макароны из твёрдых сортов", 320.0, "г", StoreAisle.GROCERY.key),
                RecipeIngredient("Филе индейки", 400.0, "г", StoreAisle.MEAT_FISH.key),
                RecipeIngredient("Томаты черри или сладкие", 250.0, "г", StoreAisle.PRODUCE.key),
                RecipeIngredient("Сыр твёрдый", 50.0, "г", StoreAisle.DAIRY_EGGS.key),
            ),
            steps = listOf(
                CookingStep(1, "Поставьте вариться макароны в подсоленной воде.", 9),
                CookingStep(2, "Параллельно потушите мелкие кубики индейки с половинками томатов под крышкой.", 10),
                CookingStep(3, "Соедините пасту с индейкой и посыпьте тёртым сыром.", 2),
            ),
        ),
    )

    fun defaultWeekPlan(): List<PlannedMealEntry> = readyMealKits().first().entries

    fun readyMealKits(): List<ReadyMealKit> {
        fun buildWeek(
            kitPrefix: String,
            breakfastIds: List<String>,
            lunchIds: List<String>,
            snackIds: List<String>,
            dinnerIds: List<String>,
            kidAdapt: Boolean,
        ): List<PlannedMealEntry> = (1..7).flatMap { day ->
            val idx = day - 1
            listOf(
                PlannedMealEntry(
                    id = "${kitPrefix}_d${day}_b",
                    dayOfWeek = day,
                    slotKey = FamilyMealSlot.BREAKFAST.key,
                    recipeId = breakfastIds[idx % breakfastIds.size],
                    servings = 4,
                    useKidAdaptation = kidAdapt,
                ),
                PlannedMealEntry(
                    id = "${kitPrefix}_d${day}_l",
                    dayOfWeek = day,
                    slotKey = FamilyMealSlot.LUNCH.key,
                    recipeId = lunchIds[idx % lunchIds.size],
                    servings = 4,
                    useKidAdaptation = kidAdapt,
                ),
                PlannedMealEntry(
                    id = "${kitPrefix}_d${day}_s",
                    dayOfWeek = day,
                    slotKey = FamilyMealSlot.SNACK_KIDS.key,
                    recipeId = snackIds[idx % snackIds.size],
                    servings = 4,
                    useKidAdaptation = true,
                ),
                PlannedMealEntry(
                    id = "${kitPrefix}_d${day}_d",
                    dayOfWeek = day,
                    slotKey = FamilyMealSlot.DINNER.key,
                    recipeId = dinnerIds[idx % dinnerIds.size],
                    servings = 4,
                    useKidAdaptation = kidAdapt,
                ),
            )
        }

        return listOf(
            ReadyMealKit(
                id = "kit_classic_family",
                title = "Домашняя классика для всей семьи",
                subtitle = "Сбалансированное меню на 7 дней с учётом постных среды и пятницы, супами и домашней выпечкой",
                badge = "Хит для семьи",
                emoji = "🏡",
                avgDailyCalories = 1180,
                avgDailyCookMinutes = 48,
                estimatedWeeklyBudgetRub = 5400,
                isKidFocused = true,
                entries = buildWeek(
                    kitPrefix = "classic",
                    breakfastIds = listOf(
                        "rec_oatmeal_apple",
                        "rec_baked_syrniki",
                        "rec_millet_pumpkin", // Среда
                        "rec_steam_omelet",
                        "rec_millet_pumpkin", // Пятница
                        "rec_baked_syrniki",
                        "rec_oatmeal_apple",
                    ),
                    lunchIds = listOf(
                        "rec_turkey_meatball_soup",
                        "rec_borscht_gentle",
                        "rec_cream_pumpkin_lentil_soup", // Среда
                        "rec_turkey_meatball_soup",
                        "rec_cream_pumpkin_lentil_soup", // Пятница
                        "rec_borscht_gentle",
                        "rec_turkey_meatball_soup",
                    ),
                    snackIds = listOf(
                        "rec_cottage_souffle",
                        "rec_baked_apples_oats",
                        "rec_berry_kissel_cookies",
                        "rec_cottage_souffle",
                        "rec_baked_apples_oats",
                        "rec_cottage_souffle",
                        "rec_berry_kissel_cookies",
                    ),
                    dinnerIds = listOf(
                        "rec_chicken_Lazy_hedgehogs",
                        "rec_baked_cod_potato",
                        "rec_buckwheat_mushrooms", // Среда
                        "rec_turkey_pasta_veggies",
                        "rec_buckwheat_mushrooms", // Пятница
                        "rec_baked_cod_potato",
                        "rec_chicken_Lazy_hedgehogs",
                    ),
                    kidAdapt = true,
                ),
            ),
            ReadyMealKit(
                id = "kit_gentle_kids",
                title = "Бережное и детское меню без жарки",
                subtitle = "Все блюда готовятся на пару, в духовке или томятся в сотейнике — идеально для малышей и чувствительного пищеварения",
                badge = "Детский стол 0+",
                emoji = "🧸",
                avgDailyCalories = 1110,
                avgDailyCookMinutes = 42,
                estimatedWeeklyBudgetRub = 4900,
                isKidFocused = true,
                entries = buildWeek(
                    kitPrefix = "kids",
                    breakfastIds = listOf(
                        "rec_oatmeal_apple",
                        "rec_steam_omelet",
                        "rec_millet_pumpkin",
                        "rec_baked_syrniki",
                        "rec_oatmeal_apple",
                        "rec_steam_omelet",
                        "rec_baked_syrniki",
                    ),
                    lunchIds = listOf(
                        "rec_turkey_meatball_soup",
                        "rec_cream_pumpkin_lentil_soup",
                        "rec_turkey_meatball_soup",
                        "rec_borscht_gentle",
                        "rec_cream_pumpkin_lentil_soup",
                        "rec_turkey_meatball_soup",
                        "rec_borscht_gentle",
                    ),
                    snackIds = listOf(
                        "rec_cottage_souffle",
                        "rec_berry_kissel_cookies",
                        "rec_baked_apples_oats",
                        "rec_cottage_souffle",
                        "rec_berry_kissel_cookies",
                        "rec_baked_apples_oats",
                        "rec_cottage_souffle",
                    ),
                    dinnerIds = listOf(
                        "rec_chicken_Lazy_hedgehogs",
                        "rec_baked_cod_potato",
                        "rec_chicken_Lazy_hedgehogs",
                        "rec_buckwheat_mushrooms",
                        "rec_baked_cod_potato",
                        "rec_chicken_Lazy_hedgehogs",
                        "rec_turkey_pasta_veggies",
                    ),
                    kidAdapt = true,
                ),
            ),
            ReadyMealKit(
                id = "kit_express_20",
                title = "Быстрые будни до 20 минут",
                subtitle = "Экспресс-рецепты из простых продуктов для загруженных дней, чтобы накормить семью вкусно и без усталости",
                badge = "Экономия времени",
                emoji = "⏱️",
                avgDailyCalories = 1150,
                avgDailyCookMinutes = 32,
                estimatedWeeklyBudgetRub = 4600,
                isKidFocused = false,
                entries = buildWeek(
                    kitPrefix = "fast",
                    breakfastIds = listOf(
                        "rec_oatmeal_apple",
                        "rec_steam_omelet",
                        "rec_oatmeal_apple",
                        "rec_steam_omelet",
                        "rec_oatmeal_apple",
                        "rec_baked_syrniki",
                        "rec_steam_omelet",
                    ),
                    lunchIds = listOf(
                        "rec_cream_pumpkin_lentil_soup",
                        "rec_turkey_meatball_soup",
                        "rec_cream_pumpkin_lentil_soup",
                        "rec_turkey_meatball_soup",
                        "rec_cream_pumpkin_lentil_soup",
                        "rec_turkey_meatball_soup",
                        "rec_cream_pumpkin_lentil_soup",
                    ),
                    snackIds = listOf(
                        "rec_berry_kissel_cookies",
                        "rec_cottage_souffle",
                        "rec_berry_kissel_cookies",
                        "rec_cottage_souffle",
                        "rec_berry_kissel_cookies",
                        "rec_baked_apples_oats",
                        "rec_cottage_souffle",
                    ),
                    dinnerIds = listOf(
                        "rec_turkey_pasta_veggies",
                        "rec_buckwheat_mushrooms",
                        "rec_turkey_pasta_veggies",
                        "rec_buckwheat_mushrooms",
                        "rec_turkey_pasta_veggies",
                        "rec_baked_cod_potato",
                        "rec_chicken_Lazy_hedgehogs",
                    ),
                    kidAdapt = true,
                ),
            ),
        )
    }
}
