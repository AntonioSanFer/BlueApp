package com.blueapp.detox

import android.os.Bundle
import android.view.View
import android.widget.TextView

/** Settings › Time options: which minute buttons appear after a pause (up to 4; the longest is the limit). */
class TimeOptionsActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        findViewById<TextView>(R.id.title).setText(R.string.time_options)
        findViewById<TextView>(R.id.intro).apply { setText(R.string.time_options_intro); visibility = View.VISIBLE }
        val foot = findViewById<TextView>(R.id.footnote).apply { visibility = View.VISIBLE }
        fun updateFoot() {
            foot.text = getString(R.string.time_options_foot, prefs.maxSessionMin)
        }
        Ui.timeOptionsList(findViewById(R.id.group), prefs) { updateFoot() }
        updateFoot()
    }
}
