package com.blueapp.detox

import android.os.Bundle
import android.widget.Button

/** Edit which apps get a pause. Changes apply immediately (the service listens for them). */
class AppsActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        val selected = HashSet(prefs.targets)
        AppListAdapter(this, selected) { prefs.targets = it }.load(findViewById(R.id.list))
        findViewById<Button>(R.id.done).setOnClickListener { finish() }
    }
}
