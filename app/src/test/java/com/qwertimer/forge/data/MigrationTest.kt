package com.qwertimer.forge.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.qwertimer.forge.data.db.ForgeDatabase
import com.qwertimer.forge.domain.model.PlanOrigin
import com.qwertimer.forge.domain.model.PlanStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the real 1 -> 2 migration against a real v1 database file.
 *
 * Room validates the post-migration schema against the current entities when it opens, so a
 * successful open here is the actual guarantee: if the ALTER statements produced anything Room did
 * not expect — a wrong type, a leftover unique index — the build of the database throws.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private var database: ForgeDatabase? = null

    @Before
    fun clean() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `v1 database migrates and keeps its history`() {
        createV1Database { db ->
            db.execSQL(
                """
                INSERT INTO workout_plans
                    (id, epochDay, focus, status, estimatedMinutes, skipReason, generatedAt, closedAt)
                VALUES (1, 20000, 'FULL_BODY', 'COMPLETED', 30, NULL, 1000, 2000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO workout_blocks
                    (id, planId, exerciseId, blockType, orderIndex, sets, reps, seconds, restSeconds, completed)
                VALUES (1, 1, 'pushup', 'MAIN', 0, 3, 12, 0, 45, 1)
                """.trimIndent(),
            )
        }

        val db = openV2()

        val plan = runBlocking { db.workoutDao().planById(1L) }
        assertThat(plan).isNotNull()
        assertThat(plan!!.plan.status).isEqualTo(PlanStatus.COMPLETED)
        assertThat(plan.plan.estimatedMinutes).isEqualTo(30)
        // Everything that existed before v2 was, by definition, the programmed session.
        assertThat(plan.plan.origin).isEqualTo(PlanOrigin.SCHEDULED)
        assertThat(plan.plan.variant).isEqualTo(0)
        assertThat(plan.blocks).hasSize(1)
        assertThat(plan.blocks.single().completed).isTrue()
    }

    @Test
    fun `a migrated database accepts a second session on the same day`() {
        createV1Database { db ->
            db.execSQL(
                """
                INSERT INTO workout_plans
                    (id, epochDay, focus, status, estimatedMinutes, skipReason, generatedAt, closedAt)
                VALUES (1, 20000, 'FULL_BODY', 'PENDING', 30, NULL, 1000, NULL)
                """.trimIndent(),
            )
        }

        val db = openV2()

        // The whole point of the migration: v1's unique index on epochDay has to be gone.
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO workout_plans
                (epochDay, focus, status, estimatedMinutes, origin, variant, generatedAt)
            VALUES (20000, 'UPPER', 'PENDING', 25, 'AD_HOC', 1, 3000)
            """.trimIndent(),
        )

        val onDay = runBlocking { db.workoutDao().scheduledPlanForDayOnce(20000L) }
        assertThat(onDay).isNotNull()
        assertThat(onDay!!.plan.origin).isEqualTo(PlanOrigin.SCHEDULED)
        assertThat(runBlocking { db.workoutDao().maxVariantForDay(20000L) }).isEqualTo(1)
    }

    private fun openV2(): ForgeDatabase =
        Room.databaseBuilder(context, ForgeDatabase::class.java, dbName)
            .addMigrations(ForgeDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
            .also {
                database = it
                // Force the open, and with it Room's schema validation.
                it.openHelper.writableDatabase
            }

    /** Builds the exact schema v1 shipped with, then stamps it as user_version 1. */
    private fun createV1Database(seed: (SupportSQLiteDatabase) -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            V1_SCHEMA.forEach(db::execSQL)
                            seed(db)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase.use { it.version = 1 }
        helper.close()
    }

    private companion object {
        /**
         * v1's DDL, reproduced verbatim. Note the UNIQUE index on `workout_plans.epochDay` — that
         * is the constraint the migration exists to remove.
         */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `food_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `barcode` TEXT, `name` TEXT NOT NULL, `brand` TEXT, `kcalPer100g` REAL NOT NULL, `proteinPer100g` REAL NOT NULL, `carbsPer100g` REAL NOT NULL, `fatPer100g` REAL NOT NULL, `servingSizeG` REAL, `servingLabel` TEXT, `source` TEXT NOT NULL, `imageUrl` TEXT, `note` TEXT, `lastUsedAt` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_food_items_barcode` ON `food_items` (`barcode`)",
            "CREATE INDEX IF NOT EXISTS `index_food_items_name` ON `food_items` (`name`)",
            "CREATE INDEX IF NOT EXISTS `index_food_items_lastUsedAt` ON `food_items` (`lastUsedAt`)",
            "CREATE TABLE IF NOT EXISTS `diary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `meal` TEXT NOT NULL, `foodId` INTEGER NOT NULL, `grams` REAL NOT NULL, `portionLabel` TEXT NOT NULL, `kcal` REAL NOT NULL, `proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, `fatG` REAL NOT NULL, `loggedAt` INTEGER NOT NULL, FOREIGN KEY(`foodId`) REFERENCES `food_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_diary_entries_epochDay` ON `diary_entries` (`epochDay`)",
            "CREATE INDEX IF NOT EXISTS `index_diary_entries_foodId` ON `diary_entries` (`foodId`)",
            "CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `region` TEXT NOT NULL, `minLevel` INTEGER NOT NULL, `impact` INTEGER NOT NULL, `timeBased` INTEGER NOT NULL, `defaultReps` INTEGER NOT NULL, `defaultSeconds` INTEGER NOT NULL, `cue` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_exercises_category` ON `exercises` (`category`)",
            "CREATE INDEX IF NOT EXISTS `index_exercises_region` ON `exercises` (`region`)",
            "CREATE TABLE IF NOT EXISTS `workout_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `focus` TEXT NOT NULL, `status` TEXT NOT NULL, `estimatedMinutes` INTEGER NOT NULL, `skipReason` TEXT, `generatedAt` INTEGER NOT NULL, `closedAt` INTEGER)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_plans_epochDay` ON `workout_plans` (`epochDay`)",
            "CREATE TABLE IF NOT EXISTS `workout_blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, `blockType` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `sets` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `seconds` INTEGER NOT NULL, `restSeconds` INTEGER NOT NULL, `completed` INTEGER NOT NULL, FOREIGN KEY(`planId`) REFERENCES `workout_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_workout_blocks_planId` ON `workout_blocks` (`planId`)",
            "CREATE INDEX IF NOT EXISTS `index_workout_blocks_exerciseId` ON `workout_blocks` (`exerciseId`)",
        )
    }
}
