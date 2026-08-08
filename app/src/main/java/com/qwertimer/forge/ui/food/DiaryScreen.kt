package com.qwertimer.forge.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qwertimer.forge.domain.model.DiaryEntry
import com.qwertimer.forge.domain.model.FoodItem
import com.qwertimer.forge.domain.model.MealType
import com.qwertimer.forge.ui.common.MacroSummary
import com.qwertimer.forge.ui.common.Pill
import com.qwertimer.forge.ui.common.SectionHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val recents by viewModel.recentFoods.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.shiftDate(-1) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                        }
                        Text(
                            text = dayLabel(state.date),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(
                            onClick = { viewModel.shiftDate(1) },
                            enabled = state.date < LocalDate.now(),
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                text = { Text("Scan") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                MacroSummary(
                    consumed = state.totals.consumed,
                    target = state.totals.target,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item {
                SearchBlock(
                    state = search,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = viewModel::runSearch,
                    onAskAi = viewModel::askAi,
                )
            }

            if (search.results.isNotEmpty()) {
                item { SectionHeader("Search results") }
                items(search.results, key = { it.barcode ?: "${it.id}:${it.name}" }) { food ->
                    FoodRow(food) { viewModel.selectFood(food) }
                }
            } else if (search.query.isBlank() && recents.isNotEmpty()) {
                item { SectionHeader("Recent") }
                items(recents, key = { it.id }) { food ->
                    FoodRow(food) { viewModel.selectFood(food) }
                }
            }

            if (state.entriesByMeal.isEmpty()) {
                item { EmptyDiaryHint() }
            }

            MealType.entries.forEach { meal ->
                val entries = state.entriesByMeal[meal].orEmpty()
                if (entries.isEmpty()) return@forEach
                item(key = "header-$meal") {
                    val kcal = entries.sumOf { it.macros.kcal }.roundToInt()
                    SectionHeader("${meal.label} · $kcal kcal")
                }
                items(entries, key = { it.id }) { entry ->
                    DiaryRow(entry) { viewModel.deleteEntry(entry.id) }
                }
            }
        }
    }

    pending?.let { portion ->
        PortionSheet(
            food = portion.food,
            suggestedGrams = portion.suggestedGrams,
            initialMeal = portion.meal,
            onDismiss = viewModel::dismissPortion,
            onConfirm = viewModel::confirmPortion,
        )
    }
}

@Composable
private fun SearchBlock(
    state: FoodSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAskAi: () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text("Search or describe what you ate") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searching) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onSearch,
                enabled = state.query.isNotBlank() && !state.searching,
                modifier = Modifier.weight(1f),
            ) { Text("Search") }

            OutlinedButton(
                onClick = onAskAi,
                enabled = state.query.isNotBlank() && !state.askingAi,
                modifier = Modifier.weight(1f),
            ) {
                if (state.askingAi) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Ask AI")
                }
            }
        }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FoodRow(food: FoodItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = food.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        food.brand?.let { append(it).append(" · ") }
                        append("${food.per100g.kcalRounded} kcal/100 g")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (food.source.isEstimate) Pill("AI")
        }
    }
}

@Composable
private fun DiaryRow(entry: DiaryEntry, onDelete: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = entry.portionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${entry.macros.kcalRounded}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${entry.name}")
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun dayLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(DAY_FORMAT)
}

/** Kept out of the lazy list so an empty diary still fills the screen sensibly. */
@Composable
private fun EmptyDiaryHint(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Scan a barcode, search a product, or describe a meal and let the AI estimate it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
