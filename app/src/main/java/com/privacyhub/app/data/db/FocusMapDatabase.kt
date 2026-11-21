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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `references` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `projectId` INTEGER NOT NULL,
            `nodeId` INTEGER NOT NULL,
            `title` TEXT NOT NULL,
            `content` TEXT NOT NULL,
            `url` TEXT NOT NULL,
            `mimeType` TEXT NOT NULL,
            `uriString` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            FOREIGN KEY(`nodeId`) REFERENCES `nodes`(`id`) ON DELETE CASCADE,
            FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_references_nodeId` ON `references` (`nodeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_references_projectId` ON `references` (`projectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_references_title` ON `references` (`title`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `routines` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `title` TEXT NOT NULL,
            `projectId` INTEGER NOT NULL,
            `nodeId` INTEGER,
            `weekdays` TEXT NOT NULL,
            `durationMinutes` INTEGER NOT NULL,
            `weeklyTarget` INTEGER NOT NULL,
            `reminderMinutes` INTEGER NOT NULL,
            `active` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routines_projectId` ON `routines` (`projectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routines_active` ON `routines` (`active`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `planned_sessions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `routineId` INTEGER NOT NULL,
            `projectId` INTEGER NOT NULL,
            `nodeId` INTEGER NOT NULL,
            `scheduledAt` INTEGER NOT NULL,
            `durationMinutes` INTEGER NOT NULL,
            `status` TEXT NOT NULL,
            `focusSessionId` INTEGER,
            FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON DELETE CASCADE,
            FOREIGN KEY(`nodeId`) REFERENCES `nodes`(`id`) ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_sessions_routineId` ON `planned_sessions` (`routineId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_sessions_scheduledAt` ON `planned_sessions` (`scheduledAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_sessions_status` ON `planned_sessions` (`status`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `weekly_reflections` (
            `weekStart` INTEGER PRIMARY KEY NOT NULL,
            `accomplished` TEXT NOT NULL,
            `incomplete` TEXT NOT NULL,
            `concepts` TEXT NOT NULL,
            `priority` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL
        )""")
    }
}

@Database(
    entities = [ProjectEntity::class, NodeEntity::class, FocusSessionEntity::class, UserSettingsEntity::class,
    FlashcardEntity::class, ReviewLogEntity::class, SchedulerSettingsEntity::class,
    ReferenceEntity::class, RoutineEntity::class, PlannedSessionEntity::class, WeeklyReflectionEntity::class,
    DetoxProfileEntity::class, DetoxAppEntity::class, NodeDetoxEntity::class,
    DetoxScheduleEntity::class, DetoxActivationEntity::class, ProjectTemplateEntity::class],
    version = 5,
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
    abstract fun referenceDao(): ReferenceDao
    abstract fun routineDao(): RoutineDao
    abstract fun plannedSessionDao(): PlannedSessionDao
    abstract fun weeklyReflectionDao(): WeeklyReflectionDao
    abstract fun detoxDao(): DetoxDao
    abstract fun projectTemplateDao(): ProjectTemplateDao


    companion object {
        @Volatile private var instance: FocusMapDatabase? = null
        fun get(context: Context): FocusMapDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, FocusMapDatabase::class.java, "focusmap.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }
    }
}
