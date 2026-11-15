package com.blueapp.detox

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.TypedValue

data class AppTheme(val id: String, val style: Int, val name: Int, val desc: Int, val minSdk: Int = 0) {
    val available get() = Build.VERSION.SDK_INT >= minSdk
}

object Themes {
    const val DEFAULT = "blue"

    val ALL = listOf(
        // Material You: primary/secondary colors pulled from your wallpaper (Android 12+).
        AppTheme("system", R.style.Theme_App_System, R.string.theme_system, R.string.theme_system_desc, minSdk = 31),
        AppTheme("blue", R.style.Theme_App_Blue, R.string.theme_blue, R.string.theme_blue_desc),
        AppTheme("amoled", R.style.Theme_App_Amoled, R.string.theme_amoled, R.string.theme_amoled_desc),
        AppTheme("light", R.style.Theme_App_Light, R.string.theme_light, R.string.theme_light_desc),
        AppTheme("forest", R.style.Theme_App_Forest, R.string.theme_forest, R.string.theme_forest_desc),
        AppTheme("sunset", R.style.Theme_App_Sunset, R.string.theme_sunset, R.string.theme_sunset_desc),
    )

    fun current(prefs: Prefs): AppTheme =
        ALL.firstOrNull { it.id == prefs.themeId && it.available } ?: ALL.first { it.id == DEFAULT }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** Resolve one of our color attributes (?attr/accentColor etc.) against a context's theme. */
fun Context.attrColor(attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return if (tv.resourceId != 0) getColor(tv.resourceId) else tv.data
}

/** Applies the chosen theme, and rebuilds itself if the theme was changed while it was in the back stack. */
open class ThemedActivity : Activity() {
    protected lateinit var prefs: Prefs
    private var appliedTheme: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        val t = Themes.current(prefs)
        appliedTheme = t.id
        setTheme(t.style)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (appliedTheme != Themes.current(prefs).id) recreate()
    }
}
