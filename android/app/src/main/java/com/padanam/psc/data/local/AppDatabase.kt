package com.padanam.psc.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SetEntity::class, QuestionEntity::class, AttemptEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        private const val DB_NAME = "padanam.db"

        /**
         * Adds the Malayalam set title, the fifth and sixth option slots and
         * the option count used by web-created questions. Existing rows keep
         * their current options.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sets ADD COLUMN titleMl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN optionEEn TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN optionFEn TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN optionEMl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN optionFMl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN optionCount INTEGER NOT NULL DEFAULT 4")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: open(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()

        /**
         * Opens the database immediately instead of waiting for the first
         * screen query. A file damaged by an interrupted write, or a failed
         * migration, used to surface later as a crash while the home screen
         * was collecting its flow. Rebuilding the local cache is safe: it is
         * a copy of the account workspace and can be downloaded again.
         */
        private fun open(context: Context): AppDatabase {
            val first = build(context)
            try {
                first.openHelper.writableDatabase
                return first
            } catch (_: Exception) {
                runCatching { first.close() }
                runCatching { context.deleteDatabase(DB_NAME) }
                val retry = build(context)
                retry.openHelper.writableDatabase
                return retry
            }
        }
    }
}
