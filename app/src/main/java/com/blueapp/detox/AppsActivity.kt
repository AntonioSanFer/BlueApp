package com.blueapp.detox

import android.app.Activity
import android.os.Bundle
import android.widget.Button

/** Edit which apps get a pause. Changes apply immediately (the service listens for them). */
class AppsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        val prefs = Prefs(this)
        val selected = HashSet(prefs.targets)
        AppListAdapter(this, selected) { prefs.targets = it }.load(findViewById(R.id.list))
        findViewById<Button>(R.id.done).setOnClickListener { finish() }
    }
}
