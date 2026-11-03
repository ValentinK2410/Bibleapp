package com.example.bible.ui.housecare

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bible.data.housecare.*

@Composable
fun FamilyMenuScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { FamilyMenuStore(context) }
    val data by store.data.collectAsState(initial = FamilyMenuData.defaultSeed())

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDay by remember { mutableIntStateOf(1) }
    val tabs = listOf("План недели", "Рецепты", "Готовые рационы", "Семья", "Покупки")

    HouseCareScaffold(
        title = "Меню для семьи",
        onBack = onBack,
        section = HouseCareSectionId.FamilyMenu,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items((1..7).toList()) { day ->
                                    FilterChip(
                                        selected = selectedDay == day,
                                        onClick = { selectedDay = day },
                                        label = { Text(WeekDays.shortLabel(day)) }
                                    )
                                }
                            }
                        }
                        item {
                            Text(
                                text = "Семейный стол: ${WeekDays.fullLabel(selectedDay)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(FamilyMealSlot.entries) { slot ->
                            val planned = data.schedule.firstOrNull { it.dayOfWeek == selectedDay && it.slotKey == slot.key }
                            val recipe = data.recipes.firstOrNull { it.id == planned?.recipeId } ?: data.recipes.firstOrNull()
                            GroupCard {
                                Text("${slot.title} · ${slot.timeHint}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                if (recipe != null) {
                                    Text(recipe.title, fontWeight = FontWeight.Bold)
                                    Text("${recipe.caloriesPerServing} ккал · ${recipe.prepMinutes + recipe.cookMinutes} мин · Б:${recipe.proteinGrams} Ж:${recipe.fatGrams} У:${recipe.carbsGrams}", style = MaterialTheme.typography.bodySmall)
                                    if (recipe.kidAdaptationNote.isNotBlank()) {
                                        Text("Детский стол: ${recipe.kidAdaptationNote}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        items(data.recipes) { recipe ->
                            GroupCard {
                                Text(recipe.title, fontWeight = FontWeight.Bold)
                                Text(recipe.subtitle, style = MaterialTheme.typography.bodySmall)
                                Text("${recipe.caloriesPerServing} ккал · ${recipe.prepMinutes + recipe.cookMinutes} мин · Б:${recipe.proteinGrams} Ж:${recipe.fatGrams} У:${recipe.carbsGrams}", style = MaterialTheme.typography.labelMedium)
                                if (recipe.kidAdaptationNote.isNotBlank()) {
                                    Text("Детям: ${recipe.kidAdaptationNote}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    else -> {
                        items(data.members) { member ->
                            GroupCard {
                                Text("${member.name} (${member.relationLabel})", fontWeight = FontWeight.Bold)
                                Text(if (member.isChildMenu) "Детское щадящее меню (порция ×${member.portionFactor})" else "Общий стол (порция ×${member.portionFactor})")
                                if (member.allergens.isNotEmpty()) {
                                    Text("Исключить: ${member.allergens.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
