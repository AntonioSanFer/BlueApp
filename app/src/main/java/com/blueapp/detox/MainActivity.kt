package com.blueapp.detox

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Dashboard: status, today's numbers, settings, pause type. */
class MainActivity : ThemedActivity() {

    private lateinit var appsValue: TextView
    private lateinit var timeValue: TextView
    private lateinit var themeValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!prefs.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.status_fix).setOnClickListener { Ui.openAccessibilitySettings(this) }
        findViewById<Button>(R.id.preview).setOnClickListener {
            startActivity(
                Intent(this, InterceptActivity::class.java)
                    .putExtra(InterceptActivity.EXTRA_PREVIEW, true)
                    .putExtra(InterceptActivity.EXTRA_PKG, prefs.targets.firstOrNull() ?: packageName)
            )
        }

        val settings = findViewById<LinearLayout>(R.id.settings)
        appsValue = Ui.navRow(settings, getString(R.string.apps), null) { open(AppsActivity::class.java) }
        timeValue = Ui.navRow(settings, getString(R.string.time_options), null) { open(TimeOptionsActivity::class.java) }
        themeValue = Ui.navRow(settings, getString(R.string.theme), null) { open(ThemesActivity::class.java) }

        Ui.segmented(
            findViewById(R.id.pause_modes),
            listOf(
                Prefs.MODE_TIMER to getString(R.string.mode_timer),
                Prefs.MODE_CHALLENGE to getString(R.string.mode_challenge),
                Prefs.MODE_BOTH to getString(R.string.mode_both),
            ),
            prefs.pauseMode,
        ) { prefs.pauseMode = it; updateModeHint() }
        updateModeHint()
    }

    private fun open(c: Class<*>) = startActivity(Intent(this, c))

    private fun updateModeHint() {
        findViewById<TextView>(R.id.pause_mode_hint).setText(
            when (prefs.pauseMode) {
                Prefs.MODE_TIMER -> R.string.mode_timer_desc
                Prefs.MODE_CHALLENGE -> R.string.mode_challenge_desc
                else -> R.string.mode_both_desc
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.onboarded || isFinishing) return

        val on = Prefs.isServiceEnabled(this)
        val targets = prefs.targets
        findViewById<TextView>(R.id.status).apply {
            text = when {
                !on -> getString(R.string.status_off)
                else -> resources.getQuantityString(R.plurals.status_on, targets.size, targets.size)
            }
            setTextColor(attrColor(if (on) R.attr.textDim else R.attr.warnColor))
        }
        findViewById<View>(R.id.status_fix).visibility = if (on) View.GONE else View.VISIBLE

        findViewById<TextView>(R.id.st_pauses).text = prefs.stat(Prefs.S_PAUSES).toString()
        findViewById<TextView>(R.id.st_left).text = prefs.stat(Prefs.S_LEFT).toString()
        findViewById<TextView>(R.id.st_minutes).text = getString(R.string.minutes_short, prefs.stat(Prefs.S_MINUTES))

        appsValue.text = Ui.appSummary(this, targets)
        timeValue.text = Ui.timeOptionsSummary(this, prefs.timeOptions)
        themeValue.setText(Themes.current(prefs).name)
    }
}
