package com.privacyhub.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ProjectEntity::class, NodeEntity::class, FocusSessionEntity::class, UserSettingsEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FocusMapDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun nodeDao(): NodeDao
    abstract fun sessionDao(): SessionDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var instance: FocusMapDatabase? = null
        fun get(context: Context): FocusMapDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, FocusMapDatabase::class.java, "focusmap.db"
            ).build().also { instance = it }
        }
    }
}
