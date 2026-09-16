package com.unsmah.workflowlens.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Single-instance Room database. All writes go through coroutines (Room's suspend
 * functions), so callers never touch a background thread directly.
 *
 * v3 adds eventType + imageSizeBytes via a real migration: history survives upgrades.
 */
@Database(
    entities = [WorkflowEvent::class],
    version = 3,
    exportSchema = false
)
abstract class WorkflowDatabase : RoomDatabase() {

    abstract fun workflowEventDao(): WorkflowEventDao

    companion object {
        @Volatile
        private var instance: WorkflowDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workflow_events ADD COLUMN eventType TEXT NOT NULL DEFAULT 'click'")
                db.execSQL("ALTER TABLE workflow_events ADD COLUMN imageSizeBytes INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): WorkflowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkflowDatabase::class.java,
                    "workflow_lens.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    // Only pre-v2 installs (which never had a migration) may still wipe.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
