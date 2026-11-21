package com.privacyhub.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Non-destructive v3 → v4: Digital Detox tables only. Existing data untouched. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `detox_profiles` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `description` TEXT NOT NULL,
            `isDefault` INTEGER NOT NULL,
            `dndEnabled` INTEGER NOT NULL,
            `pauseBehaviour` TEXT NOT NULL,
            `emergencyEnabled` INTEGER NOT NULL,
            `autoActivate` INTEGER NOT NULL,
            `projectId` INTEGER,
            `createdAt` INTEGER NOT NULL,
            FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detox_profiles_isDefault` ON `detox_profiles` (`isDefault`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detox_profiles_projectId` ON `detox_profiles` (`projectId`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `detox_apps` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `profileId` INTEGER NOT NULL,
            `packageName` TEXT NOT NULL,
            `label` TEXT NOT NULL,
            FOREIGN KEY(`profileId`) REFERENCES `detox_profiles`(`id`) ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detox_apps_profileId` ON `detox_apps` (`profileId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detox_apps_packageName` ON `detox_apps` (`packageName`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `node_detox` (
            `nodeId` INTEGER PRIMARY KEY NOT NULL,
            `profileId` INTEGER,
            `autoActivate` INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_node_detox_nodeId_profileId` ON `node_detox` (`nodeId`, `profileId`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `detox_schedules` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `profileId` INTEGER NOT NULL,
            `weekdays` TEXT NOT NULL,
            `startMinute` INTEGER NOT NULL,
            `endMinute` INTEGER NOT NULL,
            `enabled` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            FOREIGN KEY(`profileId`) REFERENCES `detox_profiles`(`id`) ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detox_schedules_enabled` ON `detox_schedules` (`enabled`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `detox_activations` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `sourceType` TEXT NOT NULL,
            `sourceId` TEXT NOT NULL,
            `profileId` INTEGER NOT NULL,
            `startedAt` INTEGER NOT NULL,
            `endsAt` INTEGER NOT NULL,
            `overrideUntil` INTEGER NOT NULL,
            `status` TEXT NOT NULL,
            `focusSessionId` INTEGER
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detox_activations_sourceType` ON `detox_activations` (`sourceType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detox_activations_sourceId` ON `detox_activations` (`sourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detox_activations_status` ON `detox_activations` (`status`)")
    }
}

/** v4 → v5: template metadata + task attributes on nodes. Additive only. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `nodes` ADD COLUMN `startDate` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `nodes` ADD COLUMN `dueDate` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `nodes` ADD COLUMN `taskStatus` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `nodes` ADD COLUMN `dependencies` TEXT NOT NULL DEFAULT ''")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `project_templates` (
            `projectId` INTEGER PRIMARY KEY NOT NULL,
            `templateId` TEXT NOT NULL,
            `templateVersion` INTEGER NOT NULL,
            `pomodoroPresets` TEXT NOT NULL,
            `detoxSuggestion` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
        )""")
    }
}
