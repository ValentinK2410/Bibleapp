package com.example.bible.ui.housecare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.housecare.CleaningHabit
import com.example.bible.data.housecare.CookingStep
import com.example.bible.data.housecare.FamilyAllergen
import com.example.bible.data.housecare.FamilyMealSlot
import com.example.bible.data.housecare.FamilyMemberProfile
import com.example.bible.data.housecare.FamilyMemberRole
import com.example.bible.data.housecare.FamilyMenuData
import com.example.bible.data.housecare.FamilyMenuDefaults
import com.example.bible.data.housecare.FamilyMenuStore
import com.example.bible.data.housecare.FamilyRecipe
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.HouseCareStore
import com.example.bible.data.housecare.PantryItem
import com.example.bible.data.housecare.PlannedMealEntry
import com.example.bible.data.housecare.ReadyMealKit
import com.example.bible.data.housecare.RecipeIngredient
import com.example.bible.data.housecare.ShoppingItem
import com.example.bible.data.housecare.StoreAisle
import com.example.bible.data.housecare.WeekDays
import com.example.bible.data.housecare.formatIngredientAmount
import com.example.bible.ui.theme.OutlinedTextField
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val shortDayDateFormatter = DateTimeFormatter.ofPattern("dd.MM")

private enum class FamilyMenuTab(val title: String, val emoji: String) {
    WEEK_PLAN("План и рационы", "📅"),
    RECIPES("Рецепты", "📖"),
    FAMILY("Семья", "👨‍👩‍👧‍👦"),
    SHOPPING("Покупки и кладовая", "🧺"),
    DAILY_SYNC("Сводка и бэкап", "✨"),
}

private enum class RecipeFilterChip(val label: String) {
    ALL("Все"),
    KIDS("Детское меню"),
    SAFE_ALLERGENS("Без аллергенов семьи"),
    FAST_25("До 25 минут"),
    FASTING("Постные блюда"),
}

/**
 * Полнофункциональный экран «Меню для семьи» внутри раздела «Попечительная о доме».
 */
@Composable
fun FamilyMenuScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val menuStore = remember(context.applicationContext) { FamilyMenuStore(context) }
    val houseCareStore = remember(context.applicationContext) { HouseCareStore(context) }

    val menuData by menuStore.data.collectAsState(initial = FamilyMenuDefaults.initialData())
    val houseCareData by houseCareStore.data.collectAsState(initial = HouseCareData.Empty)

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = FamilyMenuTab.entries.getOrElse(selectedTabIndex) { FamilyMenuTab.WEEK_PLAN }

    var selectedDayOfWeek by rememberSaveable {
        mutableIntStateOf(LocalDate.now().dayOfWeek.value.coerceIn(1, 7))
    }

    var openedRecipeForDetail by remember { mutableStateOf<Pair<FamilyRecipe, Int>?>(null) }
    var replacingSlotTarget by remember { mutableStateOf<Pair<Int, FamilyMealSlot>?>(null) }
    var showCreateRecipeDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var statusBannerText by remember { mutableStateOf<String?>(null) }

    HouseCareScaffold(
        title = "Меню для семьи",
        onBack = onBack,
        section = HouseCareSectionId.FamilyMenu,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Панель из 5 вкладок раздела
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FamilyMenuTab.entries.forEachIndexed { idx, tab ->
                    ChoiceChip(
                        label = "${tab.emoji} ${tab.title}",
                        selected = selectedTabIndex == idx,
                        onClick = { selectedTabIndex = idx },
                    )
                }
            }

            AnimatedVisibility(visible = statusBannerText != null) {
                statusBannerText?.let { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = cleaningGreen.copy(alpha = 0.14f),
                        ),
                        border = BorderStroke(1.dp, cleaningGreen.copy(alpha = 0.45f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cleaningGreen,
                                modifier = Modifier.weight(1f),
                            )
                            CompactActionIcon(
                                onClick = { statusBannerText = null },
                                contentDescription = "Закрыть уведомление",
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = cleaningGreen,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                FamilyMenuTab.WEEK_PLAN -> WeekPlanAndKitsTab(
                    menuData = menuData,
                    selectedDayOfWeek = selectedDayOfWeek,
                    onSelectDay = { selectedDayOfWeek = it },
                    onUpdateServings = { entryId, delta ->
                        scope.launch { menuStore.updateMealServings(entryId, delta) }
                    },
                    onToggleKidAdaptation = { entryId ->
                        scope.launch { menuStore.toggleMealKidAdaptation(entryId) }
                    },
                    onToggleSafeSubstitution = { entryId ->
                        scope.launch {
                            menuStore.toggleMealSafeSubstitution(entryId)
                            statusBannerText = "Безопасная замена аллергена включена для блюда"
                        }
                    },
                    onToggleCooked = { entryId ->
                        scope.launch { menuStore.toggleMealCooked(entryId) }
                    },
                    onRemoveMeal = { entryId ->
                        scope.launch { menuStore.removePlannedMeal(entryId) }
                    },
                    onChooseRecipeForSlot = { day, slot ->
                        replacingSlotTarget = day to slot
                    },
                    onOpenRecipeDetail = { recipe, servings ->
                        openedRecipeForDetail = recipe to servings
                    },
                    onApplyKit = { kit ->
                        scope.launch {
                            menuStore.applyReadyMealKit(kit)
                            statusBannerText = "Рацион «${kit.title}» применён на всю неделю"
                        }
                    },
                    onOrderKit = { kit ->
                        scope.launch {
                            menuStore.orderReadyMealKit(kit)
                            statusBannerText = "Набор продуктов «${kit.title}» сформирован в списке покупок!"
                            selectedTabIndex = FamilyMenuTab.SHOPPING.ordinal
                        }
                    },
                )

                FamilyMenuTab.RECIPES -> CookbookRecipesTab(
                    menuData = menuData,
                    onOpenRecipeDetail = { recipe ->
                        openedRecipeForDetail = recipe to menuData.recommendedServings
                    },
                    onAssignToCurrentDay = { recipe, slot ->
                        scope.launch {
                            menuStore.assignRecipeToSlot(
                                dayOfWeek = selectedDayOfWeek,
                                slot = slot,
                                recipeId = recipe.id,
                            )
                            statusBannerText = "«${recipe.title}» добавлено на ${WeekDays.shortLabel(selectedDayOfWeek)} (${slot.title})"
                        }
                    },
                    onOpenCreateRecipe = { showCreateRecipeDialog = true },
                    onDeleteRecipe = { recipeId ->
                        scope.launch { menuStore.deleteRecipe(recipeId) }
                    },
                )

                FamilyMenuTab.FAMILY -> FamilyProfilesTab(
                    menuData = menuData,
                    onToggleChildMenu = { memberId ->
                        scope.launch { menuStore.toggleMemberChildMenu(memberId) }
                    },
                    onToggleAllergen = { memberId, allergenKey ->
                        scope.launch { menuStore.toggleMemberAllergen(memberId, allergenKey) }
                    },
                    onUpdatePortion = { memberId, factor ->
                        scope.launch { menuStore.updateMemberPortion(memberId, factor) }
                    },
                    onDeleteMember = { memberId ->
                        scope.launch { menuStore.deleteMember(memberId) }
                    },
                    onAddMemberClick = { showAddMemberDialog = true },
                )

                FamilyMenuTab.SHOPPING -> ShoppingAndPantryTab(
                    context = context,
                    menuData = menuData,
                    onToggleShoppingChecked = { itemKey ->
                        scope.launch { menuStore.toggleShoppingItemChecked(itemKey) }
                    },
                    onAddCustomShopping = { name, amount, unit, aisle ->
                        scope.launch { menuStore.addCustomShoppingItem(name, amount, unit, aisle) }
                    },
                    onRemoveCustomShopping = { itemKey ->
                        scope.launch { menuStore.removeCustomShoppingItem(itemKey) }
                    },
                    onClearChecked = {
                        scope.launch { menuStore.clearCheckedShoppingItems() }
                    },
                    onUpsertPantry = { name, amount, unit, aisle ->
                        scope.launch { menuStore.upsertPantryItem(name, amount, unit, aisle) }
                    },
                    onDeletePantry = { id ->
                        scope.launch { menuStore.deletePantryItem(id) }
                    },
                    onShowBanner = { statusBannerText = it },
                )

                FamilyMenuTab.DAILY_SYNC -> UnifiedDailySyncAndBackupTab(
                    context = context,
                    menuData = menuData,
                    houseCareData = houseCareData,
                    onSyncHabits = {
                        scope.launch {
                            menuStore.syncDailyMenuHabitsToHouseCare(houseCareStore, LocalDate.now())
                            statusBannerText = "Утренние и вечерние привычки синхронизированы с меню на сегодня"
                        }
                    },
                    onExportBackup = { onResult ->
                        scope.launch {
                            val json = menuStore.exportUnifiedBackupJson(houseCareStore)
                            onResult(json)
                        }
                    },
                    onImportBackup = { rawJson ->
                        scope.launch {
                            val ok = menuStore.importUnifiedBackupJson(rawJson, houseCareStore)
                            statusBannerText = if (ok) {
                                "Данные успешно восстановлены из JSON-резервной копии"
                            } else {
                                "Не удалось распознать JSON-бэкап. Проверьте формат."
                            }
                        }
                    },
                    onShowBanner = { statusBannerText = it },
                )
            }
        }
    }

    // Детальная карточка рецепта с пересчётом порций, КБЖУ и запуском таймера из шага
    openedRecipeForDetail?.let { (recipe, initialServings) ->
        RecipeDetailDialog(
            recipe = recipe,
            initialServings = initialServings,
            menuData = menuData,
            onDismiss = { openedRecipeForDetail = null },
            onStartStepTimer = { stepTitle, minutes ->
                FifteenMinuteTimerManager.setZoneTitle(stepTitle)
                FifteenMinuteTimerManager.selectPreset(context, minutes.coerceAtLeast(1))
                FifteenMinuteTimerManager.start(context)
                statusBannerText = "Запущен таймер «$stepTitle» на $minutes мин"
            },
        )
    }

    // Диалог выбора/замены блюда для слота дня
    replacingSlotTarget?.let { (dayOfWeek, slot) ->
        SlotRecipePickerDialog(
            dayOfWeek = dayOfWeek,
            slot = slot,
            menuData = menuData,
            onDismiss = { replacingSlotTarget = null },
            onSelectRecipe = { recipe ->
                scope.launch {
                    menuStore.assignRecipeToSlot(dayOfWeek, slot, recipe.id)
                    replacingSlotTarget = null
                    statusBannerText = "Назначено блюдо «${recipe.title}» (${WeekDays.shortLabel(dayOfWeek)} · ${slot.title})"
                }
            },
        )
    }

    // Диалог создания нового домашнего рецепта
    if (showCreateRecipeDialog) {
        CreateFamilyRecipeDialog(
            onDismiss = { showCreateRecipeDialog = false },
            onSave = { newRecipe ->
                scope.launch {
                    menuStore.saveRecipe(newRecipe)
                    showCreateRecipeDialog = false
                    statusBannerText = "Домашний рецепт «${newRecipe.title}» сохранён в кулинарную книгу"
                }
            },
        )
    }

    // Диалог добавления нового члена семьи
    if (showAddMemberDialog) {
        AddFamilyMemberDialog(
            onDismiss = { showAddMemberDialog = false },
            onSave = { member ->
                scope.launch {
                    menuStore.saveMember(member)
                    showAddMemberDialog = false
                    statusBannerText = "Профиль «${member.name}» добавлен в семью"
                }
            },
        )
    }
}

// ============================================================================
// ВКЛАДКА 1: План недели и Готовые рационы
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekPlanAndKitsTab(
    menuData: FamilyMenuData,
    selectedDayOfWeek: Int,
    onSelectDay: (Int) -> Unit,
    onUpdateServings: (String, Int) -> Unit,
    onToggleKidAdaptation: (String) -> Unit,
    onToggleSafeSubstitution: (String) -> Unit,
    onToggleCooked: (String) -> Unit,
    onRemoveMeal: (String) -> Unit,
    onChooseRecipeForSlot: (Int, FamilyMealSlot) -> Unit,
    onOpenRecipeDetail: (FamilyRecipe, Int) -> Unit,
    onApplyKit: (ReadyMealKit) -> Unit,
    onOrderKit: (ReadyMealKit) -> Unit,
) {
    val weekMonday = remember { LocalDate.now().with(DayOfWeek.MONDAY) }
    val selectedDate = weekMonday.plusDays((selectedDayOfWeek - 1).toLong())
    val daySummary = menuData.daySummary(selectedDayOfWeek)
    val isFastingDay = selectedDayOfWeek == 3 || selectedDayOfWeek == 5 // Среда и пятница

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HouseCareSectionTitle("Недельное расписание питания (Пн–Вс)")
        }

        // Плитки дней недели Пн–Вс с датами, ккал и временем готовки
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..7).forEach { day ->
                    val date = weekMonday.plusDays((day - 1).toLong())
                    val sum = menuData.daySummary(day)
                    val selected = day == selectedDayOfWeek
                    val isToday = date == LocalDate.now()
                    val shape = RoundedCornerShape(18.dp)
                    val bgColor = when {
                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val borderColor = when {
                        selected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    }
                    Card(
                        modifier = Modifier
                            .width(104.dp)
                            .clip(shape)
                            .clickable { onSelectDay(day) },
                        shape = shape,
                        colors = CardDefaults.cardColors(containerColor = bgColor),
                        border = BorderStroke(if (selected) 1.8.dp else 1.dp, borderColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = WeekDays.shortLabel(day),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = date.format(shortDayDateFormatter),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "${sum.totalCalories} ккал",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "⏱ ${sum.totalCookMinutes} мин · ${sum.plannedSlotsCount}/4",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // Карточка-шапка выбранного дня
        item {
            GroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${WeekDays.fullLabel(selectedDayOfWeek)} · ${selectedDate.format(shortDayDateFormatter)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isFastingDay) {
                                    "Традиционный постный/рыбный день · щадящие домашние блюда"
                                } else {
                                    "Рекомендуемая порция семьи: ${menuData.recommendedServings} порц."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusPill(
                            text = "${daySummary.cookedSlotsCount}/${daySummary.plannedSlotsCount} готово",
                            tone = pillToneOf(daySummary.cookedSlotsCount, daySummary.plannedSlotsCount.coerceAtLeast(1)),
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        NutritionBadge("🔥 ${daySummary.totalCalories} ккал/день")
                        NutritionBadge("Б ${daySummary.totalProtein} г")
                        NutritionBadge("Ж ${daySummary.totalFat} г")
                        NutritionBadge("У ${daySummary.totalCarbs} г")
                        NutritionBadge("⏱ Готовка ${daySummary.totalCookMinutes} мин")
                    }
                }
            }
        }

        // 4 приёма пищи на день
        items(FamilyMealSlot.entries, key = { it.key }) { slot ->
            val planned = menuData.mealFor(selectedDayOfWeek, slot)
            val recipe = planned?.let { menuData.recipeById(it.recipeId) }
            val allergenWarning = recipe?.let { menuData.checkAllergenWarning(it) }

            MealSlotCard(
                slot = slot,
                planned = planned,
                recipe = recipe,
                allergenWarning = allergenWarning,
                hasChildrenInFamily = menuData.hasChildrenInFamily,
                onUpdateServings = { delta -> planned?.let { onUpdateServings(it.id, delta) } },
                onToggleKidAdaptation = { planned?.let { onToggleKidAdaptation(it.id) } },
                onToggleSafeSubstitution = { planned?.let { onToggleSafeSubstitution(it.id) } },
                onToggleCooked = { planned?.let { onToggleCooked(it.id) } },
                onReplaceClick = { onChooseRecipeForSlot(selectedDayOfWeek, slot) },
                onRemoveClick = { planned?.let { onRemoveMeal(it.id) } },
                onOpenRecipeClick = {
                    if (recipe != null && planned != null) {
                        onOpenRecipeDetail(recipe, planned.servings)
                    }
                },
            )
        }

        // Блок «Готовые семейные рационы на неделю»
        item {
            HouseCareSectionTitle("Готовые семейные рационы на 7 дней")
        }

        items(FamilyMenuDefaults.readyMealKits(), key = { it.id }) { kit ->
            val isActive = menuData.activeKitId == kit.id
            val isOrdered = menuData.orderedKitId == kit.id
            ReadyMealKitCard(
                kit = kit,
                isActive = isActive,
                isOrdered = isOrdered,
                onApplyKit = { onApplyKit(kit) },
                onOrderKit = { onOrderKit(kit) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealSlotCard(
    slot: FamilyMealSlot,
    planned: PlannedMealEntry?,
    recipe: FamilyRecipe?,
    allergenWarning: com.example.bible.data.housecare.MealAllergenWarning?,
    hasChildrenInFamily: Boolean,
    onUpdateServings: (Int) -> Unit,
    onToggleKidAdaptation: () -> Unit,
    onToggleSafeSubstitution: () -> Unit,
    onToggleCooked: () -> Unit,
    onReplaceClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onOpenRecipeClick: () -> Unit,
) {
    GroupCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BadgeCircle(size = 38.dp) {
                        Text(slot.emoji, fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = slot.fullLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (recipe != null) {
                            Text(
                                text = "⏱ ${recipe.totalMinutes} мин · ${recipe.caloriesPerServing} ккал/порц.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (planned != null && recipe != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CompactActionIcon(
                            onClick = onToggleCooked,
                            contentDescription = "Отметить готовым",
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Приготовлено",
                                tint = if (planned.isCooked) cleaningGreen else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        CompactActionIcon(
                            onClick = onReplaceClick,
                            contentDescription = "Заменить блюдо",
                        ) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = "Заменить блюдо",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        CompactActionIcon(
                            onClick = onRemoveClick,
                            contentDescription = "Убрать из плана",
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Убрать",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }

            if (planned == null || recipe == null) {
                AddInlineRow(
                    label = "Выбрать домашнее блюдо на ${slot.title.lowercase()}",
                    onClick = onReplaceClick,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                        .clickable(onClick = onOpenRecipeClick)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (planned.isCooked) TextDecoration.LineThrough else null,
                    )
                    Text(
                        text = recipe.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        NutritionBadge("Б ${recipe.proteinGrams}г · Ж ${recipe.fatGrams}г · У ${recipe.carbsGrams}г")
                        if (recipe.isKidFriendly) {
                            NutritionBadge("👶 Детский стол")
                        }
                        if (recipe.isFasting) {
                            NutritionBadge("🌿 Постное")
                        }
                    }
                }

                // Управление числом порций (- / +) и детской адаптацией
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompactActionIcon(
                            onClick = { onUpdateServings(-1) },
                            contentDescription = "Меньше порций",
                            size = 30.dp,
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = "${planned.servings} ${pluralRu(planned.servings, "порция", "порции", "порций")}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        CompactActionIcon(
                            onClick = { onUpdateServings(1) },
                            contentDescription = "Больше порций",
                            size = 30.dp,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ChoiceChip(
                            label = if (planned.useKidAdaptation) "🧸 Детям: адапт. вкл" else "🧸 Детский стол",
                            selected = planned.useKidAdaptation,
                            onClick = onToggleKidAdaptation,
                        )
                        TextButton(onClick = onOpenRecipeClick) {
                            Text("Рецепт")
                        }
                    }
                }

                // Подсказка детского стола
                if (planned.useKidAdaptation && (hasChildrenInFamily || recipe.isKidFriendly) && recipe.kidAdaptationNote.isNotBlank()) {
                    Text(
                        text = "👶 Для малыша: ${recipe.kidAdaptationNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                // Автоматическая проверка аллергенов по профилям членов семьи
                if (allergenWarning != null) {
                    val allergenTitles = allergenWarning.allergenKeys.joinToString(", ") {
                        FamilyAllergen.labelOf(it)
                    }
                    val membersText = allergenWarning.affectedMemberNames.joinToString(", ")
                    val cardBg = if (planned.useSafeSubstitution) {
                        cleaningGreen.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    }
                    val borderCol = if (planned.useSafeSubstitution) {
                        cleaningGreen.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, borderCol),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (planned.useSafeSubstitution) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = if (planned.useSafeSubstitution) cleaningGreen else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = if (planned.useSafeSubstitution) {
                                        "Безопасная замена активна ($allergenTitles)"
                                    } else {
                                        "Внимание: в рецепте есть аллерген ($allergenTitles) у: $membersText"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (planned.useSafeSubstitution) cleaningGreen else MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Text(
                                text = "Чем заменить: ${allergenWarning.substitutionAdvice}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = onToggleSafeSubstitution,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (planned.useSafeSubstitution) {
                                        "Отключить безопасную замену"
                                    } else {
                                        "Включить безопасную замену"
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyMealKitCard(
    kit: ReadyMealKit,
    isActive: Boolean,
    isOrdered: Boolean,
    onApplyKit: () -> Unit,
    onOrderKit: () -> Unit,
) {
    GroupCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    BadgeCircle(size = 42.dp) {
                        Text(kit.emoji, fontSize = 20.sp)
                    }
                    Column {
                        Text(
                            text = kit.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = kit.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (isActive) {
                    StatusPill("Активен", PillTone.Done)
                }
            }

            Text(
                text = kit.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NutritionBadge("🔥 ~${kit.avgDailyCalories} ккал/день")
                NutritionBadge("⏱ ~${kit.avgDailyCookMinutes} мин/день")
                NutritionBadge("💳 ~${kit.estimatedWeeklyBudgetRub} ₽/нед.")
                if (kit.isKidFocused) {
                    NutritionBadge("👶 С детским столом")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onApplyKit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (isActive) "Обновить неделю" else "Применить рацион")
                }
                Button(
                    onClick = onOrderKit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (isOrdered) "Набор в покупках ✓" else "Заказать продукты")
                }
            }
        }
    }
}

// ============================================================================
// ВКЛАДКА 2: Рецепты (Кулинарная книга)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CookbookRecipesTab(
    menuData: FamilyMenuData,
    onOpenRecipeDetail: (FamilyRecipe) -> Unit,
    onAssignToCurrentDay: (FamilyRecipe, FamilyMealSlot) -> Unit,
    onOpenCreateRecipe: () -> Unit,
    onDeleteRecipe: (String) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(RecipeFilterChip.ALL) }

    val filteredRecipes = remember(menuData.recipes, menuData.familyAllergenKeys, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        menuData.recipes.filter { recipe ->
            val matchesQuery = q.isEmpty() ||
                recipe.title.lowercase(Locale.ROOT).contains(q) ||
                recipe.subtitle.lowercase(Locale.ROOT).contains(q) ||
                recipe.ingredients.any { it.name.lowercase(Locale.ROOT).contains(q) }

            val matchesFilter = when (selectedFilter) {
                RecipeFilterChip.ALL -> true
                RecipeFilterChip.KIDS -> recipe.isKidFriendly
                RecipeFilterChip.SAFE_ALLERGENS -> recipe.allergens.none { it in menuData.familyAllergenKeys }
                RecipeFilterChip.FAST_25 -> recipe.totalMinutes <= 25
                RecipeFilterChip.FASTING -> recipe.isFasting
            }
            matchesQuery && matchesFilter
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск по названию блюда или ингредиенту...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecipeFilterChip.entries.forEach { chip ->
                    ChoiceChip(
                        label = chip.label,
                        selected = selectedFilter == chip,
                        onClick = { selectedFilter = chip },
                    )
                }
            }
        }

        item {
            EmptyAddCard(
                hint = "Добавить семейный рецепт",
                subtitle = "Запишите любимое домашнее блюдо с граммовками, КБЖУ и шагами",
                onClick = onOpenCreateRecipe,
            )
        }

        item {
            HouseCareSectionTitle("Домашние рецепты (${filteredRecipes.size})")
        }

        items(filteredRecipes, key = { it.id }) { recipe ->
            val warning = menuData.checkAllergenWarning(recipe)
            val isBuiltIn = recipe.id.startsWith("rec_")
            GroupCard(
                modifier = Modifier.clickable { onOpenRecipeDetail(recipe) },
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recipe.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = recipe.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!isBuiltIn) {
                            CompactActionIcon(
                                onClick = { onDeleteRecipe(recipe.id) },
                                contentDescription = "Удалить рецепт",
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        NutritionBadge("🔥 ${recipe.caloriesPerServing} ккал")
                        NutritionBadge("Б ${recipe.proteinGrams} · Ж ${recipe.fatGrams} · У ${recipe.carbsGrams}")
                        NutritionBadge("⏱ ${recipe.totalMinutes} мин")
                        if (recipe.isKidFriendly) {
                            NutritionBadge("👶 Детям")
                        }
                        if (recipe.isFasting) {
                            NutritionBadge("🌿 Постное")
                        }
                        if (warning != null) {
                            StatusPill(
                                text = "Аллерген (${warning.allergenKeys.size})",
                                tone = PillTone.Pending,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val primarySlot = FamilyMealSlot.fromKey(recipe.slotKeys.firstOrNull())
                        TextButton(onClick = { onAssignToCurrentDay(recipe, primarySlot) }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("В план (${primarySlot.title})")
                        }
                        FilledTonalButton(
                            onClick = { onOpenRecipeDetail(recipe) },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Открыть рецепт и таймер")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// ВКЛАДКА 3: Семья и предпочтения
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyProfilesTab(
    menuData: FamilyMenuData,
    onToggleChildMenu: (String) -> Unit,
    onToggleAllergen: (String, String) -> Unit,
    onUpdatePortion: (String, Float) -> Unit,
    onDeleteMember: (String) -> Unit,
    onAddMemberClick: () -> Unit,
) {
    val portionPresets = listOf(0.5f to "0.5x Малыш", 0.8f to "0.8x Школьник", 1.0f to "1.0x Стандарт", 1.2f to "1.2x Сытная")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Состав семьи: ${menuData.members.size} чел.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Рекомендуемый расчёт на кастрюлю/противень: ${menuData.recommendedServings} порц. Все аллергены автоматически проверяются в плане недели.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(menuData.members, key = { it.id }) { member ->
            GroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            BadgeCircle(size = 40.dp) {
                                Text(member.role.emoji, fontSize = 18.sp)
                            }
                            Column {
                                Text(
                                    text = member.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${member.relationLabel} · ${member.role.title} · порция ${member.portionFactor}x",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (menuData.members.size > 1) {
                            CompactActionIcon(
                                onClick = { onDeleteMember(member.id) },
                                contentDescription = "Удалить профиль",
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    // Переключатель «Детский щадящий стол»
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Детский щадящий стол (без жарки и острого)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "Включает подсказки адаптации блюд для детского пищеварения",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = member.isChildMenu,
                            onCheckedChange = { onToggleChildMenu(member.id) },
                        )
                    }

                    // Коэффициент порции
                    Text(
                        text = "Размер порции",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        portionPresets.forEach { (factor, label) ->
                            ChoiceChip(
                                label = label,
                                selected = kotlin.math.abs(member.portionFactor - factor) < 0.05f,
                                onClick = { onUpdatePortion(member.id, factor) },
                            )
                        }
                    }

                    // Исключённые аллергены
                    Text(
                        text = "Исключить аллергены и продукты:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FamilyAllergen.entries.forEach { allergen ->
                            val active = allergen.key in member.allergens
                            ChoiceChip(
                                label = "${allergen.emoji} ${allergen.title}",
                                selected = active,
                                onClick = { onToggleAllergen(member.id, allergen.key) },
                            )
                        }
                    }
                }
            }
        }

        item {
            EmptyAddCard(
                hint = "Добавить члена семьи",
                subtitle = "Укажите возрастную группу, размер порции и пищевые особенности",
                onClick = onAddMemberClick,
            )
        }
    }
}

// ============================================================================
// ВКЛАДКА 4: Список покупок и Домашняя кладовая
// ============================================================================

@Composable
private fun ShoppingAndPantryTab(
    context: Context,
    menuData: FamilyMenuData,
    onToggleShoppingChecked: (String) -> Unit,
    onAddCustomShopping: (String, Double, String, String) -> Unit,
    onRemoveCustomShopping: (String) -> Unit,
    onClearChecked: () -> Unit,
    onUpsertPantry: (String, Double, String, String) -> Unit,
    onDeletePantry: (String) -> Unit,
    onShowBanner: (String) -> Unit,
) {
    val allShoppingItems = remember(menuData) { menuData.buildWeeklyShoppingList() }
    var onlyRemainingFilter by rememberSaveable { mutableStateOf(false) }
    var showAddShoppingDialog by remember { mutableStateOf(false) }
    var showAddPantryDialog by remember { mutableStateOf(false) }

    val displayedShopping = remember(allShoppingItems, onlyRemainingFilter) {
        if (onlyRemainingFilter) {
            allShoppingItems.filter { !it.checked && it.toBuyAmount > 0.01 }
        } else {
            allShoppingItems
        }
    }
    val groupedByAisle = remember(displayedShopping) {
        displayedShopping.groupBy { StoreAisle.fromKey(it.aisle) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val remainingCount = allShoppingItems.count { !it.checked && it.toBuyAmount > 0.01 }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Список покупок на неделю",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Автоматически суммирует продукты из меню за вычетом домашней кладовой",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusPill(
                            text = "Купить: $remainingCount",
                            tone = if (remainingCount == 0) PillTone.Done else PillTone.Pending,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChoiceChip(
                            label = "Все (${allShoppingItems.size})",
                            selected = !onlyRemainingFilter,
                            modifier = Modifier.weight(1f),
                            onClick = { onlyRemainingFilter = false },
                        )
                        ChoiceChip(
                            label = "Осталось купить ($remainingCount)",
                            selected = onlyRemainingFilter,
                            modifier = Modifier.weight(1f),
                            onClick = { onlyRemainingFilter = true },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val text = formatShoppingListForShare(allShoppingItems)
                                copyTextToClipboard(context, "Список покупок семьи", text)
                                onShowBanner("Список покупок скопирован для отправки в семейный чат!")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("В семейный чат")
                        }
                        OutlinedButton(
                            onClick = { showAddShoppingDialog = true },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Своё")
                        }
                    }
                }
            }
        }

        // Отделы магазина
        StoreAisle.entries.forEach { aisle ->
            val itemsInAisle = groupedByAisle[aisle].orEmpty()
            if (itemsInAisle.isNotEmpty()) {
                item(key = "header_${aisle.key}") {
                    HouseCareSectionTitle("${aisle.emoji} ${aisle.title} (${itemsInAisle.size})")
                }
                items(itemsInAisle, key = { it.key }) { shopItem ->
                    GroupCard(
                        modifier = Modifier.clickable { onToggleShoppingChecked(shopItem.key) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Checkbox(
                                checked = shopItem.checked,
                                onCheckedChange = { onToggleShoppingChecked(shopItem.key) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = shopItem.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (shopItem.checked) TextDecoration.LineThrough else null,
                                )
                                val subtitle = buildString {
                                    if (shopItem.toBuyAmount > 0.01) {
                                        append("Купить: ${formatIngredientAmount(shopItem.toBuyAmount, shopItem.unit)}")
                                    } else {
                                        append("Полностью есть дома в кладовой")
                                    }
                                    if (shopItem.inPantryAmount > 0.01) {
                                        append(" (по плану ${formatIngredientAmount(shopItem.requiredAmount, shopItem.unit)}, дома есть ${formatIngredientAmount(shopItem.inPantryAmount, shopItem.unit)})")
                                    }
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (shopItem.inPantryAmount > 0.01) cleaningGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (shopItem.isCustom) {
                                CompactActionIcon(
                                    onClick = { onRemoveCustomShopping(shopItem.key) },
                                    contentDescription = "Удалить",
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Секция «Домашняя кладовая (Что уже есть дома)»
        item {
            HouseCareSectionTitle("🏡 Домашняя кладовая (автоматически вычитается из покупок)")
        }

        items(menuData.pantryItems, key = { it.id }) { pantry ->
            val aisle = StoreAisle.fromKey(pantry.aisle)
            GroupCard {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(aisle.emoji, fontSize = 18.sp)
                        Column {
                            Text(
                                text = pantry.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "В наличии дома: ${formatIngredientAmount(pantry.amount, pantry.unit)} · ${aisle.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cleaningGreen,
                            )
                        }
                    }
                    CompactActionIcon(
                        onClick = { onDeletePantry(pantry.id) },
                        contentDescription = "Удалить из кладовой",
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        item {
            EmptyAddCard(
                hint = "Добавить продукт в домашнюю кладовую",
                subtitle = "Укажите, что уже есть дома — это количество вычтется из списка покупок",
                onClick = { showAddPantryDialog = true },
            )
        }
    }

    if (showAddShoppingDialog) {
        AddIngredientItemDialog(
            title = "Добавить покупку вручную",
            confirmLabel = "В список покупок",
            onDismiss = { showAddShoppingDialog = false },
            onConfirm = { name, amount, unit, aisle ->
                onAddCustomShopping(name, amount, unit, aisle)
                showAddShoppingDialog = false
            },
        )
    }

    if (showAddPantryDialog) {
        AddIngredientItemDialog(
            title = "Что уже есть в кладовой дома",
            confirmLabel = "Сохранить в кладовую",
            onDismiss = { showAddPantryDialog = false },
            onConfirm = { name, amount, unit, aisle ->
                onUpsertPantry(name, amount, unit, aisle)
                showAddPantryDialog = false
            },
        )
    }
}

// ============================================================================
// ВКЛАДКА 5: Единая сводка дня и JSON-Бэкап
// ============================================================================

@Composable
private fun UnifiedDailySyncAndBackupTab(
    context: Context,
    menuData: FamilyMenuData,
    houseCareData: HouseCareData,
    onSyncHabits: () -> Unit,
    onExportBackup: ((String) -> Unit) -> Unit,
    onImportBackup: (String) -> Unit,
    onShowBanner: (String) -> Unit,
) {
    var showImportDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HouseCareUnifiedDaySummaryCard(
                menuData = menuData,
                houseCareData = houseCareData,
                onSyncHabits = onSyncHabits,
            )
        }

        item {
            HouseCareSectionTitle("Резервное копирование JSON (Уборка + Меню семьи)")
        }

        item {
            GroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Единый архив раздела «Попечительная о доме»",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Сохраняет все объекты недвижимости, план комнат, привычки, семейные профили, кулинарную книгу, рацион недели и домашнюю кладовую в один JSON-документ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                onExportBackup { json ->
                                    copyTextToClipboard(context, "HouseCare & FamilyMenu Backup", json)
                                    onShowBanner("JSON-бэкап скопирован в буфер обмена!")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Экспорт JSON")
                        }
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Импорт JSON")
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        var jsonInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Восстановление из JSON-бэкапа") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Вставьте скопированный JSON-архив раздела «Попечительная о доме»:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        label = { Text("JSON-данные") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = jsonInput.isNotBlank(),
                    onClick = {
                        onImportBackup(jsonInput.trim())
                        showImportDialog = false
                    },
                ) {
                    Text("Восстановить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

/**
 * Карточка «Единая сводка дня» (используется как внутри «Меню для семьи», так и на главном экране хаба).
 */
@Composable
internal fun HouseCareUnifiedDaySummaryCard(
    menuData: FamilyMenuData,
    houseCareData: HouseCareData,
    onSyncHabits: (() -> Unit)? = null,
    onOpenFamilyMenu: (() -> Unit)? = null,
) {
    val today = LocalDate.now()
    val dayOfWeek = today.dayOfWeek.value.coerceIn(1, 7)
    val todayMeals = menuData.mealsForDay(dayOfWeek)
    val daySummary = menuData.daySummary(dayOfWeek)

    val primaryPlace = houseCareData.places.firstOrNull()
    val todayZones = primaryPlace?.let { houseCareData.zonesOfPlace(it.id, today) }.orEmpty()
    val cleanedZones = todayZones.count { houseCareData.isZoneCleaned(it) }
    val (morningDone, morningTotal) = primaryPlace?.let {
        houseCareData.habitProgressOn(it.id, CleaningHabit.KIND_MORNING, today)
    } ?: (0 to 0)
    val (eveningDone, eveningTotal) = primaryPlace?.let {
        houseCareData.habitProgressOn(it.id, CleaningHabit.KIND_EVENING, today)
    } ?: (0 to 0)

    GroupCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "✨ Единая сводка дня (${WeekDays.fullLabel(dayOfWeek)})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Уют в доме, привычки хозяйки и питание семьи на сегодня",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = "${daySummary.totalCalories} ккал",
                    tone = PillTone.Done,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NutritionBadge("☀️ Утро: $morningDone/$morningTotal")
                NutritionBadge("🧹 Комнаты: $cleanedZones/${todayZones.size}")
                NutritionBadge("🌙 Вечер: $eveningDone/$eveningTotal")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Text(
                text = "Сегодня в меню семьи (${todayMeals.size}/4 приёма пищи · ⏱ ${daySummary.totalCookMinutes} мин):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )

            todayMeals.forEach { entry ->
                val recipe = menuData.recipeById(entry.recipeId) ?: return@forEach
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${entry.slot.emoji} ${entry.slot.title}: ${recipe.title}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${entry.servings} порц.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (onSyncHabits != null) {
                OutlinedButton(
                    onClick = onSyncHabits,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Синхронизировать утренние и вечерние привычки с меню")
                }
            } else if (onOpenFamilyMenu != null) {
                TextButton(
                    onClick = onOpenFamilyMenu,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Открыть меню семьи и список покупок →")
                }
            }
        }
    }
}

// ============================================================================
// ДИАЛОГИ: Детали рецепта с таймером, Замена слота, Создание рецепта и др.
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeDetailDialog(
    recipe: FamilyRecipe,
    initialServings: Int,
    menuData: FamilyMenuData,
    onDismiss: () -> Unit,
    onStartStepTimer: (String, Int) -> Unit,
) {
    var servings by remember { mutableIntStateOf(initialServings.coerceIn(1, 20)) }
    val scaleFactor = servings.toDouble() / recipe.baseServings.coerceAtLeast(1).toDouble()
    val completedSteps = remember(recipe.id) { mutableStateListOf<Int>() }
    val allergenWarning = remember(recipe, menuData) { menuData.checkAllergenWarning(recipe) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = recipe.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NutritionBadge("🔥 ${recipe.caloriesPerServing} ккал/порц.")
                    NutritionBadge("Б ${recipe.proteinGrams}г")
                    NutritionBadge("Ж ${recipe.fatGrams}г")
                    NutritionBadge("У ${recipe.carbsGrams}г")
                    NutritionBadge("⏱ ${recipe.totalMinutes} мин")
                }

                // Пересчёт порций
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Пересчёт продуктов на:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompactActionIcon(
                            onClick = { servings = (servings - 1).coerceAtLeast(1) },
                            contentDescription = "Меньше",
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null)
                        }
                        Text(
                            text = "$servings порц.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        CompactActionIcon(
                            onClick = { servings = (servings + 1).coerceAtMost(20) },
                            contentDescription = "Больше",
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }
                }

                if (recipe.kidAdaptationNote.isNotBlank()) {
                    Text(
                        text = "👶 Детский стол: ${recipe.kidAdaptationNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f))
                            .padding(10.dp),
                    )
                }

                if (allergenWarning != null) {
                    Text(
                        text = "⚠️ Безопасная замена аллергена: ${allergenWarning.substitutionAdvice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
                            .padding(10.dp),
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Ингредиенты (на $servings порц.):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                recipe.ingredients.forEach { ing ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "• ${ing.name}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = formatIngredientAmount(ing.scaledAmount(scaleFactor), ing.unit),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Пошаговое приготовление и таймер:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                recipe.steps.forEach { step ->
                    val done = step.stepNumber in completedSteps
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (done) completedSteps.remove(step.stepNumber) else completedSteps.add(step.stepNumber)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (done) {
                                cleaningGreen.copy(alpha = 0.10f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = done,
                                    onCheckedChange = {
                                        if (done) completedSteps.remove(step.stepNumber) else completedSteps.add(step.stepNumber)
                                    },
                                )
                                Text(
                                    text = "Шаг ${step.stepNumber}. ${step.instruction}",
                                    style = MaterialTheme.typography.bodySmall,
                                    textDecoration = if (done) TextDecoration.LineThrough else null,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (step.timerMinutes > 0) {
                                OutlinedButton(
                                    onClick = {
                                        onStartStepTimer(
                                            "${recipe.title} · Шаг ${step.stepNumber}",
                                            step.timerMinutes,
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Запустить таймер «Попечительная о доме» на ${step.timerMinutes} мин")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        },
    )
}

@Composable
private fun SlotRecipePickerDialog(
    dayOfWeek: Int,
    slot: FamilyMealSlot,
    menuData: FamilyMenuData,
    onDismiss: () -> Unit,
    onSelectRecipe: (FamilyRecipe) -> Unit,
) {
    var showAllSlots by remember { mutableStateOf(false) }
    val candidates = remember(menuData.recipes, slot, showAllSlots) {
        if (showAllSlots) {
            menuData.recipes
        } else {
            val matching = menuData.recipes.filter { slot.key in it.slotKeys }
            matching.ifEmpty { menuData.recipes }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Выбрать блюдо: ${WeekDays.fullLabel(dayOfWeek)} · ${slot.title}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChoiceChip(
                    label = if (showAllSlots) "Показаны все рецепты книги" else "Подходящие для «${slot.title}» (нажмите для всех)",
                    selected = showAllSlots,
                    onClick = { showAllSlots = !showAllSlots },
                )

                candidates.forEach { recipe ->
                    val warning = menuData.checkAllergenWarning(recipe)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectRecipe(recipe) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = recipe.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "⏱ ${recipe.totalMinutes} мин · ${recipe.caloriesPerServing} ккал" +
                                    (if (recipe.isKidFriendly) " · 👶 Детям" else "") +
                                    (if (recipe.isFasting) " · 🌿 Постное" else ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (warning != null) {
                                Text(
                                    text = "⚠️ Есть аллерген семьи (доступна безопасная замена)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateFamilyRecipeDialog(
    onDismiss: () -> Unit,
    onSave: (FamilyRecipe) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var selectedSlot by remember { mutableStateOf(FamilyMealSlot.LUNCH) }
    var cookMinutesText by remember { mutableStateOf("20") }
    var caloriesText by remember { mutableStateOf("310") }
    var kidAdaptation by remember { mutableStateOf("Готовить без обжаривания и острых специй.") }
    var isKidFriendly by remember { mutableStateOf(true) }
    var isFasting by remember { mutableStateOf(false) }
    var ingredientsRaw by remember { mutableStateOf("Картофель: 400 г\nМорковь: 150 г\nФиле индейки: 350 г") }
    var stepsRaw by remember { mutableStateOf("Подготовить и нарезать ингредиенты: 5\nТомить под крышкой на слабом огне: 20") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый домашний рецепт") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название блюда") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Краткое описание") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FamilyMealSlot.entries.forEach { slot ->
                        ChoiceChip(
                            label = "${slot.emoji} ${slot.title}",
                            selected = selectedSlot == slot,
                            onClick = { selectedSlot = slot },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cookMinutesText,
                        onValueChange = { cookMinutesText = it.filter(Char::isDigit) },
                        label = { Text("Время (мин)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = caloriesText,
                        onValueChange = { caloriesText = it.filter(Char::isDigit) },
                        label = { Text("Ккал / порц.") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChoiceChip(
                        label = "👶 Детское меню",
                        selected = isKidFriendly,
                        onClick = { isKidFriendly = !isKidFriendly },
                    )
                    ChoiceChip(
                        label = "🌿 Постное блюдо",
                        selected = isFasting,
                        onClick = { isFasting = !isFasting },
                    )
                }
                OutlinedTextField(
                    value = kidAdaptation,
                    onValueChange = { kidAdaptation = it },
                    label = { Text("Подсказка адаптации для малыша") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ingredientsRaw,
                    onValueChange = { ingredientsRaw = it },
                    label = { Text("Ингредиенты (по строке: Название: 200 г)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = stepsRaw,
                    onValueChange = { stepsRaw = it },
                    label = { Text("Шаги (по строке: Описание шага: минуты)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val parsedIngredients = ingredientsRaw.lines().mapNotNull { line ->
                        val parts = line.split(":")
                        val name = parts.getOrNull(0)?.trim().orEmpty()
                        if (name.isBlank()) return@mapNotNull null
                        val qtyTokens = parts.getOrNull(1)?.trim()?.split(" ").orEmpty()
                        val amount = qtyTokens.getOrNull(0)?.replace(",", ".")?.toDoubleOrNull() ?: 1.0
                        val unit = qtyTokens.getOrNull(1)?.ifBlank { "шт" } ?: "г"
                        RecipeIngredient(name = name, amount = amount, unit = unit)
                    }
                    val parsedSteps = stepsRaw.lines().mapIndexedNotNull { idx, line ->
                        val clean = line.trim()
                        if (clean.isBlank()) return@mapIndexedNotNull null
                        val lastColon = clean.lastIndexOf(':')
                        val text = if (lastColon > 0) clean.substring(0, lastColon).trim() else clean
                        val mins = if (lastColon > 0) clean.substring(lastColon + 1).trim().toIntOrNull() ?: 0 else 0
                        CookingStep(stepNumber = idx + 1, instruction = text, timerMinutes = mins)
                    }
                    val cookMins = cookMinutesText.toIntOrNull()?.coerceIn(1, 240) ?: 20
                    val kcal = caloriesText.toIntOrNull()?.coerceIn(30, 1500) ?: 300
                    onSave(
                        FamilyRecipe(
                            title = title.trim(),
                            subtitle = subtitle.trim().ifBlank { "Домашний семейный рецепт" },
                            slotKeys = listOf(selectedSlot.key),
                            prepMinutes = 5,
                            cookMinutes = cookMins,
                            caloriesPerServing = kcal,
                            isKidFriendly = isKidFriendly,
                            isFasting = isFasting,
                            kidAdaptationNote = kidAdaptation.trim(),
                            ingredients = parsedIngredients.ifEmpty {
                                listOf(RecipeIngredient("Домашние ингредиенты", 1.0, "порц"))
                            },
                            steps = parsedSteps.ifEmpty {
                                listOf(CookingStep(1, "Приготовить блюдо по домашнему рецепту.", cookMins))
                            },
                        ),
                    )
                },
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFamilyMemberDialog(
    onDismiss: () -> Unit,
    onSave: (FamilyMemberProfile) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Ребёнок") }
    var role by remember { mutableStateOf(FamilyMemberRole.SCHOOL_KID) }
    var isChildMenu by remember { mutableStateOf(true) }
    val selectedAllergens = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый профиль члена семьи") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя (например: Анечка, Бабушка)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("Роль в семье") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FamilyMemberRole.entries.forEach { r ->
                        ChoiceChip(
                            label = "${r.emoji} ${r.title}",
                            selected = role == r,
                            onClick = {
                                role = r
                                isChildMenu = r != FamilyMemberRole.ADULT
                            },
                        )
                    }
                }
                Text(
                    text = "Аллергены (если есть):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FamilyAllergen.entries.forEach { alg ->
                        val selected = alg.key in selectedAllergens
                        ChoiceChip(
                            label = "${alg.emoji} ${alg.title}",
                            selected = selected,
                            onClick = {
                                if (selected) selectedAllergens.remove(alg.key) else selectedAllergens.add(alg.key)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        FamilyMemberProfile(
                            name = name.trim(),
                            relationLabel = relation.trim().ifBlank { role.title },
                            role = role,
                            isChildMenu = isChildMenu,
                            allergens = selectedAllergens.toList(),
                            portionFactor = role.defaultPortionFactor,
                        ),
                    )
                },
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddIngredientItemDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("500") }
    var unit by remember { mutableStateOf("г") }
    var aisle by remember { mutableStateOf(StoreAisle.PRODUCE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название продукта (например: Творог 5%)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = { Text("Количество") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Ед. (г, мл, шт)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "Отдел:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StoreAisle.entries.forEach { a ->
                        ChoiceChip(
                            label = "${a.emoji} ${a.title}",
                            selected = aisle == a,
                            onClick = { aisle = a },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val amt = amountText.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.1) ?: 1.0
                    onConfirm(name.trim(), amt, unit.trim().ifBlank { "шт" }, aisle.key)
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun NutritionBadge(text: String) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun formatShoppingListForShare(items: List<ShoppingItem>): String {
    val toBuy = items.filter { !it.checked && it.toBuyAmount > 0.01 }
    if (toBuy.isEmpty()) return "🧺 Все продукты на неделю уже куплены или есть дома в кладовой!"
    val grouped = toBuy.groupBy { StoreAisle.fromKey(it.aisle) }
    return buildString {
        appendLine("🧺 Список покупок для семьи на неделю:")
        StoreAisle.entries.forEach { aisle ->
            val list = grouped[aisle].orEmpty()
            if (list.isNotEmpty()) {
                appendLine()
                appendLine("${aisle.emoji} ${aisle.title}:")
                list.forEach { item ->
                    appendLine("• ${item.name} — ${formatIngredientAmount(item.toBuyAmount, item.unit)}")
                }
            }
        }
    }.trim()
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
