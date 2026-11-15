package com.blueapp.detox

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.time.LocalDate

/** All persisted settings and today's counters. Plain SharedPreferences: tiny, in-memory after first read. */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("blue", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean(K_ONBOARDED, false)
        set(v) = sp.edit().putBoolean(K_ONBOARDED, v).apply()

    var targets: Set<String>
        get() = sp.getStringSet(K_TARGETS, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(K_TARGETS, v.toSet()).apply()

    /** Longest single session allowed after passing a pause, in minutes. */
    var maxSessionMin: Int
        get() = sp.getInt(K_MAX_SESSION, 10)
        set(v) = sp.edit().putInt(K_MAX_SESSION, v).apply()

    /** Last challenge kind shown, so the next one is guaranteed to be different. */
    var lastKind: String?
        get() = sp.getString(K_LAST_KIND, null)
        set(v) = sp.edit().putString(K_LAST_KIND, v).apply()

    // ---- Today's stats (reset automatically when the date changes) ----

    fun stat(key: String): Int {
        rollDay()
        return sp.getInt(key, 0)
    }

    fun bump(key: String, by: Int = 1) {
        rollDay()
        sp.edit().putInt(key, sp.getInt(key, 0) + by).apply()
    }

    private fun rollDay() {
        val today = LocalDate.now().toString()
        if (sp.getString(K_DAY, null) != today) {
            sp.edit()
                .putString(K_DAY, today)
                .putInt(S_PAUSES, 0).putInt(S_LEFT, 0).putInt(S_SESSIONS, 0)
                .putInt(S_MINUTES, 0).putInt(S_EXTENSIONS, 0)
                .apply()
        }
    }

    companion object {
        const val K_ONBOARDED = "onboarded"
        const val K_TARGETS = "targets"
        const val K_MAX_SESSION = "max_session"
        const val K_LAST_KIND = "last_kind"
        const val K_DAY = "day"

        const val S_PAUSES = "s_pauses"         // times the pause screen appeared
        const val S_LEFT = "s_left"             // times you walked away instead
        const val S_SESSIONS = "s_sessions"     // sessions started
        const val S_MINUTES = "s_minutes"       // minutes granted
        const val S_EXTENSIONS = "s_extensions" // "one more" after time ran out

        val SESSION_CHOICES = intArrayOf(5, 10, 15, 20, 30, 45)

        /** Package names of the recommended apps. TikTok ships under several IDs by region. */
        val RECOMMENDED = linkedMapOf(
            "com.zhiliaoapp.musically" to "TikTok",
            "com.ss.android.ugc.trill" to "TikTok",
            "com.ss.android.ugc.aweme" to "TikTok",
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube",
        )

        fun isServiceEnabled(context: Context): Boolean {
            val me = ComponentName(context, GuardService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
