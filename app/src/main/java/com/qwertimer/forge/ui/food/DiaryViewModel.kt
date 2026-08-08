package com.qwertimer.forge.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwertimer.forge.data.repo.DiaryRepository
import com.qwertimer.forge.data.repo.FoodRepository
import com.qwertimer.forge.domain.model.DayTotals
import com.qwertimer.forge.domain.model.DiaryEntry
import com.qwertimer.forge.domain.model.FoodItem
import com.qwertimer.forge.domain.model.MealType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiaryUiState(
    val date: LocalDate = LocalDate.now(),
    val entriesByMeal: Map<MealType, List<DiaryEntry>> = emptyMap(),
    val totals: DayTotals = DayTotals(),
)

data class FoodSearchState(
    val query: String = "",
    val results: List<FoodItem> = emptyList(),
    val searching: Boolean = false,
    val askingAi: Boolean = false,
    val message: String? = null,
)

/** A food that has been identified and is waiting for a portion. */
data class PendingPortion(
    val food: FoodItem,
    val suggestedGrams: Double? = null,
    val meal: MealType = MealType.forHour(LocalTime.now().hour),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val diary: DiaryRepository,
    private val foods: FoodRepository,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val _search = MutableStateFlow(FoodSearchState())
    val search: StateFlow<FoodSearchState> = _search.asStateFlow()

    private val _pending = MutableStateFlow<PendingPortion?>(null)
    val pending: StateFlow<PendingPortion?> = _pending.asStateFlow()

    val recentFoods: StateFlow<List<FoodItem>> = foods.recentFoods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<DiaryUiState> = selectedDate
        .flatMapLatest { date ->
            combine(diary.entriesFor(date), diary.totalsFor(date)) { entries, totals ->
                DiaryUiState(
                    date = date,
                    entriesByMeal = entries.groupBy { it.meal },
                    totals = totals,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryUiState())

    fun shiftDate(days: Long) {
        selectedDate.update { it.plusDays(days) }
    }

    fun onQueryChange(query: String) {
        _search.update { it.copy(query = query, message = null) }
    }

    fun runSearch() {
        val query = _search.value.query
        if (query.isBlank()) return
        viewModelScope.launch {
            _search.update { it.copy(searching = true, message = null) }
            foods.search(query).fold(
                onSuccess = { results ->
                    _search.update {
                        it.copy(
                            searching = false,
                            results = results,
                            message = if (results.isEmpty()) {
                                "Nothing found. Try \"Ask AI\" to estimate it instead."
                            } else {
                                null
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _search.update {
                        it.copy(searching = false, message = error.message ?: "Search failed")
                    }
                },
            )
        }
    }

    /** For food that has no packet: "a bowl of pho", "two weetbix with milk". */
    fun askAi() {
        val query = _search.value.query
        if (query.isBlank()) return
        viewModelScope.launch {
            _search.update { it.copy(askingAi = true, message = null) }
            foods.estimateWithAi(query).fold(
                onSuccess = { (food, grams) ->
                    _search.update { it.copy(askingAi = false) }
                    _pending.value = PendingPortion(food, grams)
                },
                onFailure = { error ->
                    _search.update {
                        it.copy(askingAi = false, message = error.message ?: "AI lookup failed")
                    }
                },
            )
        }
    }

    fun selectFood(food: FoodItem) {
        viewModelScope.launch {
            // Search results from Open Food Facts are not in the database yet, so persist first —
            // a diary entry needs a real row to point at.
            val stored = if (food.id == 0L) foods.persist(food) else food
            _pending.value = PendingPortion(stored)
        }
    }

    fun dismissPortion() {
        _pending.value = null
    }

    fun confirmPortion(grams: Double, label: String, meal: MealType) {
        val food = _pending.value?.food ?: return
        viewModelScope.launch {
            diary.log(
                food = food,
                grams = grams,
                portionLabel = label,
                meal = meal,
                date = selectedDate.value,
            )
            _pending.value = null
            _search.update { FoodSearchState() }
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { diary.delete(id) }
    }
}
