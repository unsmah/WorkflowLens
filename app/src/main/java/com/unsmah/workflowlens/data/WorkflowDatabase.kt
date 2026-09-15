package com.unsmah.workflowlens.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Single-instance Room database. All writes go through coroutines (Room's suspend functions),
 * so callers never touch a background thread directly.
 */
@Database(
    entities = [WorkflowEvent::class],
    version = 2,
    exportSchema = false
)
abstract class WorkflowDatabase : RoomDatabase() {

    abstract fun workflowEventDao(): WorkflowEventDao

    companion object {
        @Volatile
        private var instance: WorkflowDatabase? = null

        fun get(context: Context): WorkflowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkflowDatabase::class.java,
                    "workflow_lens.db"
                )
                    // v1→v2 only adds a nullable failureNote column; wipe is fine for a
                    // tracker timeline, but only because the feature is non-critical.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
