package com.blueapp.detox

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper

/** First run: pick apps (TikTok / Instagram / YouTube pre-selected), session length, then permissions. */
class OnboardingActivity : ThemedActivity() {

    private lateinit var flipper: ViewFlipper
    private lateinit var next: Button
    private lateinit var back: Button
    private lateinit var step: TextView
    private val selected = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        flipper = findViewById(R.id.flipper)
        next = findViewById(R.id.next)
        back = findViewById(R.id.back)
        step = findViewById(R.id.step)

        // Apps
        selected += prefs.targets
        val firstTime = selected.isEmpty()
        AppListAdapter(this, selected) { prefs.targets = it }.load(findViewById<ListView>(R.id.list)) { apps ->
            if (firstTime) {
                apps.filter { it.recommended }.forEach { selected += it.pkg }
                prefs.targets = selected
                (findViewById<ListView>(R.id.list).adapter as AppListAdapter).notifyDataSetChanged()
            }
            if (apps.none { it.recommended }) {
                findViewById<TextView>(R.id.apps_body).setText(R.string.apps_body_none_installed)
            }
        }

        // Session length
        Ui.minuteChips(findViewById(R.id.choices), prefs.maxSessionMin) { prefs.maxSessionMin = it }

        // Permissions
        setupPermCards()

        next.setOnClickListener { go(+1) }
        back.setOnClickListener { go(-1) }
        render()
    }

    override fun onResume() {
        super.onResume()
        refreshPerms()
    }

    private fun go(delta: Int) {
        val i = flipper.displayedChild
        if (delta > 0 && i == 1 && selected.isEmpty()) {
            Toast.makeText(this, R.string.pick_one, Toast.LENGTH_SHORT).show()
            return
        }
        if (delta > 0 && i == flipper.childCount - 1) {
            finishOnboarding(); return
        }
        flipper.displayedChild = (i + delta).coerceIn(0, flipper.childCount - 1)
        render()
    }

    private fun render() {
        val i = flipper.displayedChild
        step.text = getString(R.string.step_of, i + 1, flipper.childCount)
        back.visibility = if (i == 0) View.INVISIBLE else View.VISIBLE
        next.setText(
            when (i) {
                0 -> R.string.get_started
                flipper.childCount - 1 -> R.string.finish
                else -> R.string.next
            }
        )
    }

    private fun finishOnboarding() {
        if (!Prefs.isServiceEnabled(this)) {
            Toast.makeText(this, R.string.finish_without_access, Toast.LENGTH_LONG).show()
        }
        prefs.onboarded = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private lateinit var access: View
    private lateinit var overlay: View

    private fun setupPermCards() {
        access = findViewById(R.id.perm_access)
        overlay = findViewById(R.id.perm_overlay)

        access.findViewById<TextView>(R.id.perm_name).setText(R.string.perm_access_name)
        access.findViewById<TextView>(R.id.perm_desc).setText(R.string.perm_access_desc)
        access.findViewById<Button>(R.id.perm_grant).apply {
            setText(R.string.enable)
            setOnClickListener { Ui.openAccessibilitySettings(this@OnboardingActivity) }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            access.findViewById<TextView>(R.id.perm_hint).apply {
                setText(R.string.perm_restricted_hint); visibility = View.VISIBLE
            }
            access.findViewById<Button>(R.id.perm_extra).apply {
                setText(R.string.app_info); visibility = View.VISIBLE
                setOnClickListener { Ui.openAppInfo(this@OnboardingActivity) }
            }
        }

        overlay.findViewById<TextView>(R.id.perm_name).setText(R.string.perm_overlay_name)
        overlay.findViewById<TextView>(R.id.perm_desc).setText(R.string.perm_overlay_desc)
        overlay.findViewById<Button>(R.id.perm_grant).apply {
            setText(R.string.allow)
            setOnClickListener { Ui.openOverlaySettings(this@OnboardingActivity) }
        }
    }

    private fun refreshPerms() {
        stateOf(access, Prefs.isServiceEnabled(this), R.string.required)
        stateOf(overlay, Ui.canOverlay(this), R.string.recommended)
    }

    private fun stateOf(card: View, granted: Boolean, missingLabel: Int) {
        card.findViewById<TextView>(R.id.perm_state).apply {
            setText(if (granted) R.string.granted else missingLabel)
            setTextColor(attrColor(if (granted) R.attr.goodColor else R.attr.warnColor))
        }
        card.findViewById<Button>(R.id.perm_grant).visibility = if (granted) View.GONE else View.VISIBLE
        if (granted) {
            card.findViewById<View>(R.id.perm_hint).visibility = View.GONE
            card.findViewById<View>(R.id.perm_extra).visibility = View.GONE
        }
    }
}
