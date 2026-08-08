package com.qwertimer.forge.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwertimer.forge.data.prefs.SettingsRepository
import com.qwertimer.forge.data.repo.TrainingRepository
import com.qwertimer.forge.domain.model.ComplianceStats
import com.qwertimer.forge.domain.model.WorkoutPlan
import com.qwertimer.forge.enforce.EnforcementScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutUiState(
    val loading: Boolean = true,
    val plan: WorkoutPlan? = null,
    val isRestDay: Boolean = false,
    val stats: ComplianceStats? = null,
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val training: TrainingRepository,
    private val settings: SettingsRepository,
    private val scheduler: EnforcementScheduler,
) : ViewModel() {

    private val today = LocalDate.now()

    private val _state = MutableStateFlow(WorkoutUiState())
    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    val stats: StateFlow<ComplianceStats?> = training.stats(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            // Generating up front means the plan exists before the first nag needs to describe it.
            training.ensurePlanFor(today)
            val config = settings.current()
            _state.update { it.copy(isRestDay = !config.isTrainingDay(today.dayOfWeek)) }
        }
        viewModelScope.launch {
            training.planFor(today).collect { plan ->
                _state.update { it.copy(loading = false, plan = plan) }
            }
        }
    }

    fun toggleBlock(blockId: Long, completed: Boolean) {
        viewModelScope.launch { training.setBlockCompleted(blockId, completed) }
    }

    fun markComplete() {
        val planId = _state.value.plan?.id ?: return
        viewModelScope.launch {
            training.markCompleted(planId)
            // Stop the escalation immediately rather than waiting for the next alarm to notice.
            scheduler.scheduleNextReminder()
        }
    }

    fun skip(reason: String) {
        val planId = _state.value.plan?.id ?: return
        viewModelScope.launch {
            training.markSkipped(planId, reason)
            scheduler.scheduleNextReminder()
        }
    }

    fun regenerate() {
        viewModelScope.launch { training.regenerate(today) }
    }
}
