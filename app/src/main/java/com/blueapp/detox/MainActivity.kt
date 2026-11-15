package com.blueapp.detox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

/** Dashboard: is protection on, today's numbers, which apps, how long a session may last. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (!prefs.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.status_fix).setOnClickListener { Ui.openAccessibilitySettings(this) }
        findViewById<Button>(R.id.edit_apps).setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        findViewById<Button>(R.id.preview).setOnClickListener {
            startActivity(
                Intent(this, InterceptActivity::class.java)
                    .putExtra(InterceptActivity.EXTRA_PREVIEW, true)
                    .putExtra(InterceptActivity.EXTRA_PKG, prefs.targets.firstOrNull() ?: packageName)
            )
        }
        Ui.minuteChips(findViewById(R.id.choices), prefs.maxSessionMin) { prefs.maxSessionMin = it }
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.onboarded) return

        val on = Prefs.isServiceEnabled(this)
        val targets = prefs.targets
        findViewById<TextView>(R.id.status_title).apply {
            setText(if (on) R.string.status_on else R.string.status_off)
            setTextColor(getColor(if (on) R.color.good else R.color.warn))
        }
        findViewById<TextView>(R.id.status_body).text = when {
            !on -> getString(R.string.status_off_body)
            targets.isEmpty() -> getString(R.string.status_no_apps)
            else -> getString(R.string.status_on_body)
        }
        findViewById<View>(R.id.status_fix).visibility = if (on) View.GONE else View.VISIBLE

        stat(R.id.st_pauses, prefs.stat(Prefs.S_PAUSES), R.string.st_pauses)
        stat(R.id.st_left, prefs.stat(Prefs.S_LEFT), R.string.st_left)
        stat(R.id.st_sessions, prefs.stat(Prefs.S_SESSIONS), R.string.st_sessions)
        stat(R.id.st_minutes, prefs.stat(Prefs.S_MINUTES), R.string.st_minutes)

        findViewById<TextView>(R.id.apps_summary).text = Ui.appSummary(this, targets)
    }

    private fun stat(id: Int, value: Int, label: Int) {
        val cell = findViewById<View>(id)
        cell.findViewById<TextView>(R.id.value).text = value.toString()
        cell.findViewById<TextView>(R.id.name).setText(label)
    }
}
