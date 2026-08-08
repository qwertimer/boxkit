package com.qwertimer.forge.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.qwertimer.forge.data.db.ForgeDatabase
import com.qwertimer.forge.data.db.WorkoutDao
import com.qwertimer.forge.data.db.WorkoutPlanEntity
import com.qwertimer.forge.domain.model.PlanOrigin
import com.qwertimer.forge.domain.model.PlanStatus
import com.qwertimer.forge.domain.model.SessionFocus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The rules that keep ad-hoc training from polluting the programme live in SQL, not in Kotlin, so
 * this is where they get proven: a rest-day session must never look like a scheduled one.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutDaoTest {

    private lateinit var db: ForgeDatabase
    private lateinit var dao: WorkoutDao

    private val monday = 20_000L
    private val restDay = 20_001L

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.workoutDao()
    }

    @After
    fun tearDown() = db.close()

    private fun plan(
        day: Long,
        origin: PlanOrigin,
        status: PlanStatus = PlanStatus.PENDING,
        variant: Int = 0,
        focus: SessionFocus = SessionFocus.FULL_BODY,
    ) = WorkoutPlanEntity(
        epochDay = day,
        focus = focus,
        status = status,
        estimatedMinutes = 30,
        origin = origin,
        variant = variant,
        generatedAt = 0L,
    )

    @Test
    fun `several sessions can share a day`() = runBlocking {
        dao.insertPlan(plan(monday, PlanOrigin.SCHEDULED))
        dao.insertPlan(plan(monday, PlanOrigin.AD_HOC, variant = 1))
        dao.insertPlan(plan(monday, PlanOrigin.AD_HOC, variant = 2))

        assertThat(dao.plansForDay(monday).first()).hasSize(3)
    }

    @Test
    fun `the programmed session is listed first`() = runBlocking {
        // Inserted after the ad-hoc one, so id order alone would put it second.
        dao.insertPlan(plan(monday, PlanOrigin.AD_HOC, variant = 1))
        dao.insertPlan(plan(monday, PlanOrigin.SCHEDULED))

        val ordered = dao.plansForDay(monday).first().map { it.plan.origin }

        assertThat(ordered).containsExactly(PlanOrigin.SCHEDULED, PlanOrigin.AD_HOC).inOrder()
    }

    @Test
    fun `the scheduled lookup ignores ad-hoc sessions`() = runBlocking {
        dao.insertPlan(plan(restDay, PlanOrigin.AD_HOC, variant = 1))

        assertThat(dao.scheduledPlanForDayOnce(restDay)).isNull()

        dao.insertPlan(plan(restDay, PlanOrigin.SCHEDULED))
        assertThat(dao.scheduledPlanForDayOnce(restDay)?.plan?.origin)
            .isEqualTo(PlanOrigin.SCHEDULED)
    }

    @Test
    fun `compliance history only sees the programmed sessions`() = runBlocking {
        dao.insertPlan(plan(monday, PlanOrigin.SCHEDULED, PlanStatus.COMPLETED))
        dao.insertPlan(plan(monday, PlanOrigin.AD_HOC, PlanStatus.COMPLETED, variant = 1))
        dao.insertPlan(plan(restDay, PlanOrigin.AD_HOC, PlanStatus.COMPLETED, variant = 1))

        val statuses = dao.scheduledStatusesSince(0L).first()

        assertThat(statuses).hasSize(1)
        assertThat(statuses.single().epochDay).isEqualTo(monday)
    }

    @Test
    fun `bonus count covers only completed ad-hoc work`() = runBlocking {
        dao.insertPlan(plan(monday, PlanOrigin.SCHEDULED, PlanStatus.COMPLETED))
        dao.insertPlan(plan(monday, PlanOrigin.AD_HOC, PlanStatus.COMPLETED, variant = 1))
        dao.insertPlan(plan(restDay, PlanOrigin.AD_HOC, PlanStatus.COMPLETED, variant = 1))
        dao.insertPlan(plan(restDay, PlanOrigin.AD_HOC, PlanStatus.PENDING, variant = 2))

        assertThat(dao.bonusCompletedSince(0L).first()).isEqualTo(2)
    }

    @Test
    fun `next variant is one past the highest used that day`() = runBlocking {
        assertThat(dao.maxVariantForDay(monday)).isEqualTo(-1)

        dao.insertPlan(plan(monday, PlanOrigin.SCHEDULED, variant = 0))
        dao.insertPlan(plan(monday, PlanOrigin.AD_HOC, variant = 3))

        assertThat(dao.maxVariantForDay(monday)).isEqualTo(3)
        // Variants are per-day, so a different day starts over.
        assertThat(dao.maxVariantForDay(restDay)).isEqualTo(-1)
    }

    @Test
    fun `deleting one session leaves the others alone`() = runBlocking {
        val scheduled = dao.insertPlan(plan(monday, PlanOrigin.SCHEDULED))
        val extra = dao.insertPlan(plan(monday, PlanOrigin.AD_HOC, variant = 1))

        dao.deletePlan(extra)

        val remaining = dao.plansForDay(monday).first()
        assertThat(remaining).hasSize(1)
        assertThat(remaining.single().plan.id).isEqualTo(scheduled)
    }
}
