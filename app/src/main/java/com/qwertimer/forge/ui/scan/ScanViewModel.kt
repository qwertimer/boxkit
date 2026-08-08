package com.qwertimer.forge.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwertimer.forge.data.repo.BarcodeLookup
import com.qwertimer.forge.data.repo.DiaryRepository
import com.qwertimer.forge.data.repo.FoodRepository
import com.qwertimer.forge.domain.model.MealType
import com.qwertimer.forge.ui.food.PendingPortion
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val looking: Boolean = false,
    val lastBarcode: String? = null,
    val message: String? = null,
    val pending: PendingPortion? = null,
    val logged: String? = null,
) {
    /** Pause the analyser while a lookup or the portion sheet is on screen. */
    val scanningPaused: Boolean get() = looking || pending != null
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun onBarcode(raw: String) {
        val current = _state.value
        // The analyser fires many times a second on the same barcode; only act on a change.
        if (current.scanningPaused || raw == current.lastBarcode) return

        _state.update { it.copy(looking = true, lastBarcode = raw, message = null, logged = null) }
        viewModelScope.launch {
            when (val result = foods.lookupBarcode(raw)) {
                is BarcodeLookup.Found -> _state.update {
                    it.copy(
                        looking = false,
                        pending = PendingPortion(result.food, result.suggestedGrams),
                    )
                }

                is BarcodeLookup.Unknown -> _state.update {
                    it.copy(looking = false, message = "${result.barcode}: ${result.detail}")
                }

                is BarcodeLookup.Failed -> _state.update {
                    it.copy(looking = false, message = result.message)
                }
            }
        }
    }

    fun dismissPortion() {
        // Clear the last barcode too, so the same item can be scanned again straight away.
        _state.update { it.copy(pending = null, lastBarcode = null) }
    }

    fun confirmPortion(grams: Double, label: String, meal: MealType) {
        val food = _state.value.pending?.food ?: return
        viewModelScope.launch {
            diary.log(food, grams, label, meal, LocalDate.now())
            _state.update {
                it.copy(
                    pending = null,
                    lastBarcode = null,
                    logged = "${food.name} · ${food.per100g.forGrams(grams).kcalRounded} kcal",
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, lastBarcode = null) }
    }
}
