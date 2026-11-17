package com.modernnavi.keaprep

import android.app.Application

class KeaPrepApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // WorkManager observes connectivity and uses the public, key-free live-feed cache.
        AutoContentSyncScheduler.schedule(this)
        val tutorPreferences = getSharedPreferences("kea_offline_study", MODE_PRIVATE)
        if (tutorPreferences.getBoolean("tutor_reminders_enabled", false)) {
            TutorReminderScheduler.schedule(
                this,
                tutorPreferences.getString("tutor_reminder_times", TutorReminderScheduler.DEFAULT_TIMES)
                    ?: TutorReminderScheduler.DEFAULT_TIMES,
            )
        }
    }
}
