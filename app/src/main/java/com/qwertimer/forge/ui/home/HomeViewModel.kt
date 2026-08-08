package com.qwertimer.forge.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwertimer.forge.data.repo.DiaryRepository
import com.qwertimer.forge.data.repo.TrainingRepository
import com.qwertimer.forge.domain.model.ComplianceStats
import com.qwertimer.forge.domain.model.DayTotals
import com.qwertimer.forge.domain.model.PlanStatus
import com.qwertimer.forge.domain.model.WorkoutPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val date: LocalDate = LocalDate.now(),
    val totals: DayTotals = DayTotals(),
    val plan: WorkoutPlan? = null,
    val stats: ComplianceStats? = null,
) {
    val trainingHeadline: String
        get() = when (plan?.status) {
            null -> "Rest day"
            PlanStatus.COMPLETED -> "Session done. ${plan.focus.label}."
            PlanStatus.SKIPPED -> "Skipped today."
            PlanStatus.PENDING -> "${plan.focus.label} · ~${plan.estimatedMinutes} min"
        }

    val trainingDetail: String?
        get() = when (plan?.status) {
            null -> "Nothing scheduled. Enjoy it."
            PlanStatus.PENDING ->
                "${plan.completedBlocks} of ${plan.blocks.size} movements ticked off."

            PlanStatus.SKIPPED -> plan.skipReason
            PlanStatus.COMPLETED -> null
        }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val diary: DiaryRepository,
    private val training: TrainingRepository,
) : ViewModel() {

    private val today = LocalDate.now()

    val state: StateFlow<HomeUiState> = combine(
        diary.totalsFor(today),
        training.planFor(today),
        training.stats(today),
    ) { totals, plan, stats ->
        HomeUiState(date = today, totals = totals, plan = plan, stats = stats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { training.ensurePlanFor(today) }
    }
}
