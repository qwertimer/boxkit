package com.qwertimer.forge.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwertimer.forge.data.prefs.SettingsRepository
import com.qwertimer.forge.data.repo.DiaryRepository
import com.qwertimer.forge.data.repo.TrainingRepository
import com.qwertimer.forge.domain.model.ComplianceStats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val CHART_DAYS = 14L

data class StatsUiState(
    val stats: ComplianceStats = ComplianceStats(0, 0, 0, 0, 0, 0),
    /** One entry per day for the last two weeks, oldest first. Missing days read as zero. */
    val kcalHistory: List<Double> = emptyList(),
    val kcalTarget: Double = 0.0,
) {
    val averageKcal: Double
        get() = kcalHistory.takeIf { it.isNotEmpty() }?.average() ?: 0.0
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    diary: DiaryRepository,
    training: TrainingRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val today = LocalDate.now()
    private val from = today.minusDays(CHART_DAYS - 1)

    val state: StateFlow<StatsUiState> = combine(
        training.stats(today),
        diary.kcalBetween(from, today),
        settings.settings,
    ) { stats, kcalByDay, config ->
        StatsUiState(
            stats = stats,
            kcalHistory = (0 until CHART_DAYS).map { offset ->
                kcalByDay[from.plusDays(offset)] ?: 0.0
            },
            kcalTarget = config.kcalTarget,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}
