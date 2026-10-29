package com.duminda.homebeacon

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("home_beacon_prefs", Context.MODE_PRIVATE)

    var trustedNumbers: Set<String>
        get() {
            val set = prefs.getStringSet(KEY_TRUSTED_NUMBERS, null)
            if (set != null) {
                return set
            }
            @Suppress("DEPRECATION")
            val oldSingle = prefs.getString("trusted_number", "") ?: ""
            if (oldSingle.isNotBlank()) {
                val newSet = setOf(oldSingle)
                prefs.edit().putStringSet(KEY_TRUSTED_NUMBERS, newSet).apply()
                return newSet
            }
            return emptySet()
        }
        set(value) {
            prefs.edit().putStringSet(KEY_TRUSTED_NUMBERS, value).apply()
        }

    fun addTrustedNumber(number: String): Boolean {
        val trimmed = number.trim()
        if (trimmed.isBlank()) return false
        val current = trustedNumbers.toMutableSet()
        val added = current.add(trimmed)
        if (added) {
            trustedNumbers = current
        }
        return added
    }

    fun removeTrustedNumber(number: String): Boolean {
        val current = trustedNumbers.toMutableSet()
        val removed = current.remove(number)
        if (removed) {
            trustedNumbers = current
        }
        return removed
    }

    var triggerPhrase: String
        get() {
            val saved = prefs.getString(KEY_TRIGGER_PHRASE, null)
            if (saved != null) return saved
            val word = triggerWord
            val code = secretCode
            return if (word.isNotBlank() && code.isNotBlank()) "$word $code" else if (code.isNotBlank()) code else "WHERE 1234"
        }
        set(value) {
            prefs.edit().putString(KEY_TRIGGER_PHRASE, value.trim()).apply()
        }

    var triggerWord: String
        get() = prefs.getString(KEY_TRIGGER_WORD, "WHERE") ?: "WHERE"
        set(value) {
            prefs.edit().putString(KEY_TRIGGER_WORD, value.trim().uppercase()).apply()
        }

    var secretCode: String
        get() = prefs.getString(KEY_SECRET_CODE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SECRET_CODE, value.trim()).apply()
        }

    var isRepliesEnabled: Boolean
        get() = prefs.getBoolean(KEY_REPLIES_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_REPLIES_ENABLED, value).apply()
        }

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        set(value) {
            prefs.edit().putInt(KEY_THEME_MODE, value).apply()
        }

    companion object {
        private const val KEY_TRUSTED_NUMBERS = "trusted_numbers_set"
        private const val KEY_TRIGGER_PHRASE = "trigger_phrase"
        private const val KEY_TRIGGER_WORD = "trigger_word"
        private const val KEY_SECRET_CODE = "secret_code"
        private const val KEY_REPLIES_ENABLED = "replies_enabled"
        private const val KEY_THEME_MODE = "theme_mode"

        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }
}
