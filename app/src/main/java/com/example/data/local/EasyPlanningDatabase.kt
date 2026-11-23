package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ShiftSeriesEntity::class,
        ShiftOverrideEntity::class,
        OvertimeEntryEntity::class,
        AllowanceEntryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class EasyPlanningDatabase : RoomDatabase() {
    abstract fun dao(): EasyPlanningDao

    companion object {
        @Volatile
        private var INSTANCE: EasyPlanningDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shift_series ADD COLUMN defaultOvertimeJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE shift_series ADD COLUMN defaultAllowancesJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE shift_overrides ADD COLUMN customOvertimeJson TEXT")
                db.execSQL("ALTER TABLE shift_overrides ADD COLUMN customAllowancesJson TEXT")
            }
        }

        fun getInstance(context: Context): EasyPlanningDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EasyPlanningDatabase::class.java,
                    "easyplanning_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        @androidx.annotation.VisibleForTesting
        internal fun resetForTesting() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
