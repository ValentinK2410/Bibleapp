package com.example.bible.data.housecare

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.familyMenuDataStore: DataStore<Preferences> by preferencesDataStore(name = "family_menu")

private object FamilyMenuKeys {
    val DOCUMENT_JSON = stringPreferencesKey("family_menu_json")
}

/** Хранилище подраздела «Меню для семьи» внутри «Попечительной о доме». */
class FamilyMenuStore(context: Context) {

    private val app = context.applicationContext

    val data: Flow<FamilyMenuData> = app.familyMenuDataStore.data.map { prefs ->
        FamilyMenuData.parse(prefs[FamilyMenuKeys.DOCUMENT_JSON])
    }

    suspend fun snapshot(): FamilyMenuData = data.first()

    suspend fun save(data: FamilyMenuData) {
        app.familyMenuDataStore.edit { prefs ->
            prefs[FamilyMenuKeys.DOCUMENT_JSON] = data.toJson().toString()
        }
    }

    private suspend fun mutate(block: (FamilyMenuData) -> FamilyMenuData) {
        save(block(snapshot()))
    }

    /** Назначить или заменить рецепт на выбранный день недели (1..7) и приём пищи. */
    suspend fun assignRecipeToSlot(
        dayOfWeek: Int,
        slot: FamilyMealSlot,
        recipeId: String,
        servings: Int? = null,
    ) = mutate { cur ->
        val day = dayOfWeek.coerceIn(1, 7)
        val existing = cur.mealFor(day, slot)
        val nextServings = servings ?: existing?.servings ?: cur.recommendedServings
        val updatedEntry = PlannedMealEntry(
            id = existing?.id ?: UUID.randomUUID().toString(),
            dayOfWeek = day,
            slotKey = slot.key,
            recipeId = recipeId,
            servings = nextServings.coerceIn(1, 20),
            useKidAdaptation = existing?.useKidAdaptation ?: cur.hasChildrenInFamily,
            useSafeSubstitution = existing?.useSafeSubstitution ?: false,
            isCooked = false,
        )
        val filtered = cur.weekPlan.filterNot { it.dayOfWeek == day && it.slotKey == slot.key }
        cur.copy(weekPlan = filtered + updatedEntry)
    }

    /** Изменить количество порций запланированного блюда (`-` / `+`). */
    suspend fun updateMealServings(entryId: String, delta: Int) = mutate { cur ->
        cur.copy(
            weekPlan = cur.weekPlan.map { entry ->
                if (entry.id == entryId) {
                    entry.copy(servings = (entry.servings + delta).coerceIn(1, 20))
                } else {
                    entry
                }
            },
        )
    }

    /** Включить или выключить безопасную замену аллергенов для запланированного блюда. */
    suspend fun toggleMealSafeSubstitution(entryId: String) = mutate { cur ->
        cur.copy(
            weekPlan = cur.weekPlan.map { entry ->
                if (entry.id == entryId) {
                    entry.copy(useSafeSubstitution = !entry.useSafeSubstitution)
                } else {
                    entry
                }
            },
        )
    }

    /** Включить или выключить адаптацию для детского стола. */
    suspend fun toggleMealKidAdaptation(entryId: String) = mutate { cur ->
        cur.copy(
            weekPlan = cur.weekPlan.map { entry ->
                if (entry.id == entryId) {
                    entry.copy(useKidAdaptation = !entry.useKidAdaptation)
                } else {
                    entry
                }
            },
        )
    }

    /** Отметить блюдо приготовленным или снять отметку. */
    suspend fun toggleMealCooked(entryId: String) = mutate { cur ->
        cur.copy(
            weekPlan = cur.weekPlan.map { entry ->
                if (entry.id == entryId) {
                    entry.copy(isCooked = !entry.isCooked)
                } else {
                    entry
                }
            },
        )
    }

    /** Удалить блюдо из расписания дня. */
    suspend fun removePlannedMeal(entryId: String) = mutate { cur ->
        cur.copy(weekPlan = cur.weekPlan.filterNot { it.id == entryId })
    }

    /** Применить готовый семейный рацион на всю неделю (с учётом рекомендуемого числа порций семьи). */
    suspend fun applyReadyMealKit(kit: ReadyMealKit) = mutate { cur ->
        val recServings = cur.recommendedServings
        val adaptedEntries = kit.entries.map { item ->
            val recipe = cur.recipeById(item.recipeId)
            val hasAllergenConflict = recipe != null && cur.checkAllergenWarning(recipe) != null
            item.copy(
                id = UUID.randomUUID().toString(),
                servings = recServings,
                useKidAdaptation = cur.hasChildrenInFamily || kit.isKidFocused,
                useSafeSubstitution = hasAllergenConflict,
                isCooked = false,
            )
        }
        cur.copy(
            weekPlan = adaptedEntries,
            activeKitId = kit.id,
        )
    }

    /** Оформить заказ набора продуктов по готовому рациону и обновить план покупок. */
    suspend fun orderReadyMealKit(kit: ReadyMealKit) = mutate { cur ->
        val recServings = cur.recommendedServings
        val adaptedEntries = kit.entries.map { item ->
            val recipe = cur.recipeById(item.recipeId)
            val hasAllergenConflict = recipe != null && cur.checkAllergenWarning(recipe) != null
            item.copy(
                id = UUID.randomUUID().toString(),
                servings = recServings,
                useKidAdaptation = cur.hasChildrenInFamily || kit.isKidFocused,
                useSafeSubstitution = hasAllergenConflict,
            )
        }
        cur.copy(
            weekPlan = adaptedEntries,
            activeKitId = kit.id,
            orderedKitId = kit.id,
            checkedShoppingKeys = emptySet(),
        )
    }

    /** Добавить или обновить домашний рецепт в кулинарной книге. */
    suspend fun saveRecipe(recipe: FamilyRecipe) = mutate { cur ->
        val exists = cur.recipes.any { it.id == recipe.id }
        val nextRecipes = if (exists) {
            cur.recipes.map { if (it.id == recipe.id) recipe else it }
        } else {
            listOf(recipe) + cur.recipes
        }
        cur.copy(recipes = nextRecipes)
    }

    suspend fun deleteRecipe(recipeId: String) = mutate { cur ->
        cur.copy(
            recipes = cur.recipes.filterNot { it.id == recipeId },
            weekPlan = cur.weekPlan.filterNot { it.recipeId == recipeId },
        )
    }

    /** Добавить или обновить профиль члена семьи. */
    suspend fun saveMember(member: FamilyMemberProfile) = mutate { cur ->
        val exists = cur.members.any { it.id == member.id }
        val nextMembers = if (exists) {
            cur.members.map { if (it.id == member.id) member else it }
        } else {
            cur.members + member
        }
        cur.copy(members = nextMembers)
    }

    suspend fun toggleMemberChildMenu(memberId: String) = mutate { cur ->
        cur.copy(
            members = cur.members.map { m ->
                if (m.id == memberId) m.copy(isChildMenu = !m.isChildMenu) else m
            },
        )
    }

    suspend fun toggleMemberAllergen(memberId: String, allergenKey: String) = mutate { cur ->
        cur.copy(
            members = cur.members.map { m ->
                if (m.id != memberId) {
                    m
                } else {
                    val next = if (allergenKey in m.allergens) {
                        m.allergens - allergenKey
                    } else {
                        m.allergens + allergenKey
                    }
                    m.copy(allergens = next)
                }
            },
        )
    }

    suspend fun updateMemberPortion(memberId: String, portionFactor: Float) = mutate { cur ->
        cur.copy(
            members = cur.members.map { m ->
                if (m.id == memberId) m.copy(portionFactor = portionFactor.coerceIn(0.3f, 2.0f)) else m
            },
        )
    }

    suspend fun deleteMember(memberId: String) = mutate { cur ->
        if (cur.members.size <= 1) return@mutate cur
        cur.copy(members = cur.members.filterNot { it.id == memberId })
    }

    /** Добавить или обновить продукт в домашней кладовой. */
    suspend fun upsertPantryItem(name: String, amount: Double, unit: String, aisle: String) = mutate { cur ->
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@mutate cur
        val newItem = PantryItem(
            name = cleanName,
            amount = amount.coerceAtLeast(0.0),
            unit = unit.trim().ifBlank { "г" },
            aisle = aisle,
        )
        val normKey = newItem.normalizedKey()
        val existing = cur.pantryItems.firstOrNull { it.normalizedKey() == normKey }
        val nextPantry = if (existing != null) {
            cur.pantryItems.map {
                if (it.id == existing.id) it.copy(amount = amount.coerceAtLeast(0.0), aisle = aisle) else it
            }
        } else {
            cur.pantryItems + newItem
        }
        cur.copy(pantryItems = nextPantry)
    }

    suspend fun deletePantryItem(itemId: String) = mutate { cur ->
        cur.copy(pantryItems = cur.pantryItems.filterNot { it.id == itemId })
    }

    /** Добавить свою покупку вручную. */
    suspend fun addCustomShoppingItem(name: String, amount: Double, unit: String, aisle: String) = mutate { cur ->
        val clean = name.trim()
        if (clean.isBlank()) return@mutate cur
        val id = "custom:${UUID.randomUUID()}"
        val item = ShoppingItem(
            id = id,
            key = id,
            name = clean,
            requiredAmount = amount.coerceAtLeast(0.1),
            inPantryAmount = 0.0,
            toBuyAmount = amount.coerceAtLeast(0.1),
            unit = unit.trim().ifBlank { "шт" },
            aisle = aisle,
            checked = false,
            isCustom = true,
        )
        cur.copy(customShoppingItems = cur.customShoppingItems + item)
    }

    /** Отметить позицию списка покупок купленной или снять галочку. */
    suspend fun toggleShoppingItemChecked(itemKey: String) = mutate { cur ->
        val nextChecked = if (itemKey in cur.checkedShoppingKeys) {
            cur.checkedShoppingKeys - itemKey
        } else {
            cur.checkedShoppingKeys + itemKey
        }
        cur.copy(checkedShoppingKeys = nextChecked)
    }

    suspend fun removeCustomShoppingItem(itemKey: String) = mutate { cur ->
        cur.copy(
            customShoppingItems = cur.customShoppingItems.filterNot { it.key == itemKey || it.id == itemKey },
            checkedShoppingKeys = cur.checkedShoppingKeys - itemKey,
        )
    }

    suspend fun clearCheckedShoppingItems() = mutate { cur ->
        cur.copy(
            customShoppingItems = cur.customShoppingItems.filterNot { it.key in cur.checkedShoppingKeys },
            checkedShoppingKeys = emptySet(),
        )
    }

    /**
     * Синхронизирует утренние и вечерние привычки хозяйки в `HouseCareStore`
     * с расписанием меню семьи на текущий день.
     */
    suspend fun syncDailyMenuHabitsToHouseCare(
        houseCareStore: HouseCareStore,
        date: LocalDate = LocalDate.now(),
    ) {
        val menu = snapshot()
        val hc = houseCareStore.snapshot()
        val placeId = hc.places.firstOrNull()?.id ?: run {
            houseCareStore.addPlace("Дом", "", 1)
            houseCareStore.snapshot().places.firstOrNull()?.id ?: return
        }

        val dayMeals = menu.mealsForDay(date.dayOfWeek.value)
        val breakfastTitle = dayMeals.firstOrNull { it.slotKey == FamilyMealSlot.BREAKFAST.key }
            ?.let { menu.recipeById(it.recipeId)?.title }
        val dinnerTitle = dayMeals.firstOrNull { it.slotKey == FamilyMealSlot.DINNER.key }
            ?.let { menu.recipeById(it.recipeId)?.title }

        val morningHabitName = if (!breakfastTitle.isNullOrBlank()) {
            "🍽️ Приготовить завтрак семьи: $breakfastTitle"
        } else {
            "🍽️ Приготовить тёплый завтрак для семьи"
        }
        val eveningHabitName = if (!dinnerTitle.isNullOrBlank()) {
            "🕯️ Семейный ужин и чистая кухня: $dinnerTitle"
        } else {
            "🕯️ Семейный ужин и подготовка кухни к утру"
        }

        val currentHc = houseCareStore.snapshot()
        val existingMorning = currentHc.habitsOf(placeId, CleaningHabit.KIND_MORNING)
            .firstOrNull { it.name.startsWith("🍽️") }
        if (existingMorning == null) {
            houseCareStore.addHabit(placeId, CleaningHabit.KIND_MORNING, morningHabitName)
        } else {
            houseCareStore.updateHabit(existingMorning.id, morningHabitName, CleaningHabit.KIND_MORNING)
        }

        val existingEvening = currentHc.habitsOf(placeId, CleaningHabit.KIND_EVENING)
            .firstOrNull { it.name.startsWith("🕯️") }
        if (existingEvening == null) {
            houseCareStore.addHabit(placeId, CleaningHabit.KIND_EVENING, eveningHabitName)
        } else {
            houseCareStore.updateHabit(existingEvening.id, eveningHabitName, CleaningHabit.KIND_EVENING)
        }
    }

    /** Экспортирует единый JSON-бэкап «Попечительной о доме» (Уборка + Меню для семьи). */
    suspend fun exportUnifiedBackupJson(houseCareStore: HouseCareStore): String {
        val menu = snapshot()
        val cleaning = houseCareStore.snapshot()
        return JSONObject().apply {
            put("version", 2)
            put("exportedAtMs", System.currentTimeMillis())
            put("houseCare", cleaning.toJson())
            put("familyMenu", menu.toJson())
        }.toString(2)
    }

    /** Импортирует единый JSON-бэкап (поддерживает как полный архив, так и только `familyMenu`). */
    suspend fun importUnifiedBackupJson(
        rawJson: String,
        houseCareStore: HouseCareStore,
    ): Boolean {
        if (rawJson.isBlank()) return false
        return try {
            val root = JSONObject(rawJson)
            when {
                root.has("familyMenu") || root.has("houseCare") -> {
                    root.optJSONObject("familyMenu")?.let { fmObj ->
                        save(FamilyMenuData.parse(fmObj.toString()))
                    }
                    root.optJSONObject("houseCare")?.let { hcObj ->
                        houseCareStore.save(HouseCareData.parse(hcObj.toString()))
                    }
                    true
                }
                root.has("weekPlan") && root.has("recipes") -> {
                    save(FamilyMenuData.parse(root.toString()))
                    true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}
