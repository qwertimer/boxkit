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
    val sessions: List<WorkoutPlan> = emptyList(),
    val stats: ComplianceStats? = null,
) {
    /** The programmed session, absent on a rest day. */
    val plan: WorkoutPlan? get() = sessions.firstOrNull { it.isScheduled }

    private val extras: List<WorkoutPlan> get() = sessions.filterNot { it.isScheduled }

    val hasPending: Boolean get() = sessions.any { it.status == PlanStatus.PENDING }

    val trainingHeadline: String
        get() {
            val scheduled = plan ?: return when {
                extras.any { it.status == PlanStatus.PENDING } -> "Rest day · bonus session ready"
                extras.any { it.status == PlanStatus.COMPLETED } -> "Rest day · bonus session done"
                else -> "Rest day"
            }
            return when (scheduled.status) {
                PlanStatus.COMPLETED -> "Session done. ${scheduled.focus.label}."
                PlanStatus.SKIPPED -> "Skipped today."
                PlanStatus.PENDING -> "${scheduled.focus.label} · ~${scheduled.estimatedMinutes} min"
            }
        }

    val trainingDetail: String?
        get() {
            val scheduled = plan ?: return when {
                extras.isEmpty() -> "Nothing scheduled. Enjoy it."
                else -> "${extras.size} extra session${if (extras.size == 1) "" else "s"} today."
            }
            val extraNote = if (extras.isEmpty()) {
                ""
            } else {
                " Plus ${extras.size} extra."
            }
            return when (scheduled.status) {
                PlanStatus.PENDING ->
                    "${scheduled.completedBlocks} of ${scheduled.blocks.size} movements ticked off."

                PlanStatus.SKIPPED -> scheduled.skipReason
                PlanStatus.COMPLETED -> extraNote.trim().ifBlank { null }
            }
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
        training.sessionsFor(today),
        training.stats(today),
    ) { totals, sessions, stats ->
        HomeUiState(date = today, totals = totals, sessions = sessions, stats = stats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { training.ensurePlanFor(today) }
    }
}
