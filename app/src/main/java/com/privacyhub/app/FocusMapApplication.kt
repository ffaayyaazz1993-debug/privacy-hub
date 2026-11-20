package com.privacyhub.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.privacyhub.app.data.db.FocusMapDatabase
import com.privacyhub.app.data.repo.FocusMapRepository

class FocusMapApplication : Application() {
    val database by lazy { FocusMapDatabase.get(this) }
    val repository by lazy { FocusMapRepository(database) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_FOCUS, "Focus sessions", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val CHANNEL_FOCUS = "focusmap_focus"
        lateinit var instance: FocusMapApplication
            private set
    }
}
