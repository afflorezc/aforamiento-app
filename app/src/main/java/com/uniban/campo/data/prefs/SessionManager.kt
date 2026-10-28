package com.uniban.campo.data.prefs

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false)
        private set(value) = prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        private set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    fun login(email: String) {
        this.email = email
        isLoggedIn = true
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_EMAIL = "email"
    }
}
