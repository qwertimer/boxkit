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
    val sessions: List<WorkoutPlan> = emptyList(),
    val isRestDay: Boolean = false,
    val working: Boolean = false,
) {
    /** The programmed session, if today is a training day. */
    val scheduled: WorkoutPlan? get() = sessions.firstOrNull { it.isScheduled }

    val extras: List<WorkoutPlan> get() = sessions.filterNot { it.isScheduled }
}

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
            training.sessionsFor(today).collect { sessions ->
                _state.update { it.copy(loading = false, sessions = sessions) }
            }
        }
    }

    /** Add a session on top of whatever today already has — including on a rest day. */
    fun addSession() = work { training.addSession(today) }

    fun reroll(planId: Long) = work { training.reroll(planId) }

    fun deleteSession(planId: Long) = work { training.deleteSession(planId) }

    fun toggleBlock(blockId: Long, completed: Boolean) {
        viewModelScope.launch { training.setBlockCompleted(blockId, completed) }
    }

    fun markComplete(planId: Long) {
        viewModelScope.launch {
            training.markCompleted(planId)
            resettleAlarmsIfScheduled(planId)
        }
    }

    fun skip(planId: Long, reason: String) {
        viewModelScope.launch {
            training.markSkipped(planId, reason)
            resettleAlarmsIfScheduled(planId)
        }
    }

    /**
     * Only closing out the *programmed* session ends today's escalation. Finishing a bonus session
     * must not, or one keen rest-day workout would silently disarm the nag for a training day.
     */
    private suspend fun resettleAlarmsIfScheduled(planId: Long) {
        if (_state.value.sessions.firstOrNull { it.id == planId }?.isScheduled == true) {
            scheduler.scheduleNextReminder()
        }
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            try {
                block()
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }
}
