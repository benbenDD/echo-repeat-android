package com.echoenglish.app

import android.app.Application
import com.echoenglish.app.data.AppDatabase
import com.echoenglish.app.data.SettingsRepository

class EchoEnglishApp : Application() {
    val database by lazy { AppDatabase(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
}
