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

    /**
     * The minutes offered on "How long do you need?" after a pause, chosen in Settings.
     * The longest one is also the most a single session can last.
     */
    var timeOptions: List<Int>
        get() = sp.getString(K_TIME_OPTIONS, null)
            ?.split(',')?.mapNotNull { it.toIntOrNull() }?.filter { it in TIME_CHOICES }?.sorted()
            ?.ifEmpty { null } ?: DEFAULT_TIME_OPTIONS
        set(v) = sp.edit().putString(K_TIME_OPTIONS, v.distinct().sorted().joinToString(",")).apply()

    val maxSessionMin: Int get() = timeOptions.max()

    /**
     * Which exercises a pause can show. Stored as the ones you turned *off*, so exercises
     * added in later versions start out on. Never empty.
     */
    var exercises: Set<String>
        get() {
            val off = sp.getStringSet(K_EXERCISES_OFF, null) ?: legacyOff()
            return Exercises.ALL.filter { it !in off }.toSet().ifEmpty { Exercises.ALL.toSet() }
        }
        set(v) = sp.edit().putStringSet(K_EXERCISES_OFF, Exercises.ALL.filter { it !in v }.toSet()).apply()

    /** Before blocks there was a Timer / Challenge / Both switch; carry that choice over. */
    private fun legacyOff(): Set<String> = when (sp.getString(K_PAUSE_MODE, null)) {
        "timer" -> Exercises.ALL.toSet() - "countdown"
        "challenge" -> setOf("countdown")
        else -> emptySet()
    }

    var themeId: String
        get() = sp.getString(K_THEME, Themes.DEFAULT)!!
        set(v) = sp.edit().putString(K_THEME, v).apply()

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
        const val K_TIME_OPTIONS = "time_options"
        const val K_LAST_KIND = "last_kind"
        const val K_DAY = "day"
        const val K_THEME = "theme"
        const val K_PAUSE_MODE = "pause_mode" // old Timer / Challenge / Both switch, read once for migration
        const val K_EXERCISES_OFF = "exercises_off"

        const val S_PAUSES = "s_pauses"         // times the pause screen appeared
        const val S_LEFT = "s_left"             // times you walked away instead
        const val S_SESSIONS = "s_sessions"     // sessions started
        const val S_MINUTES = "s_minutes"       // minutes granted
        const val S_EXTENSIONS = "s_extensions" // "one more" after time ran out

        /** Everything you can pick from in Settings, and how many you can pick. */
        val TIME_CHOICES = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60)
        const val MAX_TIME_OPTIONS = 4
        val DEFAULT_TIME_OPTIONS = listOf(2, 5, 10)

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
