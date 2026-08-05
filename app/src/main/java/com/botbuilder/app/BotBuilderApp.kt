package com.botbuilder.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.botbuilder.app.billing.BillingManager
import com.botbuilder.app.data.local.SecureStore

class BotBuilderApp : Application() {

    lateinit var billingManager: BillingManager
        private set

    override fun onCreate() {
        super.onCreate()
        val secureStore = SecureStore(applicationContext)

        applyThemeMode(secureStore.themeMode)

        billingManager = BillingManager(applicationContext, secureStore) { /* tier cached in SecureStore; screens re-read it as needed */ }
        billingManager.startConnection()
    }

    companion object {
        /** Applies the saved theme choice app-wide. Call again (from Profile) whenever
         *  the user changes it, followed by activity.recreate() to repaint immediately. */
        fun applyThemeMode(mode: String) {
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
