package com.duminda.homebeacon

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("home_beacon_prefs", Context.MODE_PRIVATE)

    var trustedNumber: String
        get() = prefs.getString(KEY_TRUSTED_NUMBER, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_TRUSTED_NUMBER, value).apply()
        }

    var secretCode: String
        get() = prefs.getString(KEY_SECRET_CODE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SECRET_CODE, value).apply()
        }

    var isRepliesEnabled: Boolean
        get() = prefs.getBoolean(KEY_REPLIES_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_REPLIES_ENABLED, value).apply()
        }

    companion object {
        private const val KEY_TRUSTED_NUMBER = "trusted_number"
        private const val KEY_SECRET_CODE = "secret_code"
        private const val KEY_REPLIES_ENABLED = "replies_enabled"
    }
}
