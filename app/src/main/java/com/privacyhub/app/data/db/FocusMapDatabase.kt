package com.privacyhub.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters

val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `flashcards` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `projectId` INTEGER NOT NULL,
                `nodeId` INTEGER NOT NULL,
                `question` TEXT NOT NULL,
                `answer` TEXT NOT NULL,
                `hint` TEXT NOT NULL,
                `explanation` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `dueTimestamp` INTEGER NOT NULL,
                `lastReviewTimestamp` INTEGER NOT NULL,
                `stability` REAL NOT NULL,
                `difficulty` REAL NOT NULL,
                `repetitions` INTEGER NOT NULL,
                `lapses` INTEGER NOT NULL,
                `learningState` TEXT NOT NULL,
                `learningStep` INTEGER NOT NULL,
                `intervalDays` INTEGER NOT NULL,
                `schedulerVersion` INTEGER NOT NULL,
                `reviewStatus` TEXT NOT NULL,
                `archived` INTEGER NOT NULL,
                FOREIGN KEY(`nodeId`) REFERENCES `nodes`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
            )""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_nodeId` ON `flashcards` (`nodeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_projectId` ON `flashcards` (`projectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_dueTimestamp` ON `flashcards` (`dueTimestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_archived` ON `flashcards` (`archived`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `review_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `flashcardId` INTEGER NOT NULL,
                `reviewedAt` INTEGER NOT NULL,
                `rating` TEXT NOT NULL,
                `previousDueTimestamp` INTEGER NOT NULL,
                `nextDueTimestamp` INTEGER NOT NULL,
                `elapsedDays` REAL NOT NULL,
                `previousStability` REAL NOT NULL,
                `newStability` REAL NOT NULL,
                `previousDifficulty` REAL NOT NULL,
                `newDifficulty` REAL NOT NULL,
                `elapsedDurationSeconds` INTEGER NOT NULL,
                `schedulerVersion` INTEGER NOT NULL,
                FOREIGN KEY(`flashcardId`) REFERENCES `flashcards`(`id`) ON DELETE CASCADE
            )""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_flashcardId` ON `review_logs` (`flashcardId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_reviewedAt` ON `review_logs` (`reviewedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_rating` ON `review_logs` (`rating`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `scheduler_settings` (
                `id` INTEGER PRIMARY KEY NOT NULL,
                `desiredRetention` REAL NOT NULL,
                `maximumInterval` INTEGER NOT NULL,
                `newCardsPerDay` INTEGER NOT NULL,
                `reviewLimitPerDay` INTEGER NOT NULL,
                `reminderEnabled` INTEGER NOT NULL,
                `preferredReviewHour` INTEGER NOT NULL
            )""")
        }
    }

@Database(
    entities = [ProjectEntity::class, NodeEntity::class, FocusSessionEntity::class, UserSettingsEntity::class,
    FlashcardEntity::class, ReviewLogEntity::class, SchedulerSettingsEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FocusMapDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun nodeDao(): NodeDao
    abstract fun sessionDao(): SessionDao
    abstract fun settingsDao(): SettingsDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun schedulerSettingsDao(): SchedulerSettingsDao2


    companion object {
        @Volatile private var instance: FocusMapDatabase? = null
        fun get(context: Context): FocusMapDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, FocusMapDatabase::class.java, "focusmap.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
