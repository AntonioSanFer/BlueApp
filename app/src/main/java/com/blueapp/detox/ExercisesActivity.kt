package com.blueapp.detox

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Settings › Exercises: every exercise, grouped by block, each with its own switch and a "Try" link. */
class ExercisesActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        findViewById<TextView>(R.id.title).setText(R.string.exercises)
        findViewById<TextView>(R.id.intro).apply { setText(R.string.exercises_intro); visibility = View.VISIBLE }
        findViewById<TextView>(R.id.footnote).apply { setText(R.string.exercises_hint); visibility = View.VISIBLE }

        // activity_list has one group; this screen needs one per block, so they go in its place.
        val template = findViewById<LinearLayout>(R.id.group)
        val content = template.parent as LinearLayout
        var at = content.indexOfChild(template)
        template.visibility = View.GONE

        Exercises.BLOCKS.forEachIndexed { b, (block, kinds) ->
            content.addView(TextView(this, null, 0, R.style.Section).apply {
                setText(Challenge.blockName(block))
            }, at++, LinearLayout.LayoutParams(-2, -2).apply {
                topMargin = dp(if (b == 0) 20 else 24); bottomMargin = dp(8)
            })
            val group = LinearLayout(this, null, 0, R.style.Group).apply { orientation = LinearLayout.VERTICAL }
            content.addView(group, at++, LinearLayout.LayoutParams(-1, -2))
            kinds.forEach { kind -> row(group, kind) }
        }
    }

    private fun row(group: LinearLayout, kind: String) {
        val info = Challenge.KINDS.getValue(kind)
        val tryIt = TextView(this).apply {
            setText(R.string.try_one)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(attrColor(R.attr.accentColor))
            minHeight = dp(48)
            setPadding(dp(12), 0, dp(12), 0)
            background = getDrawable(R.drawable.row_bg)
            contentDescription = getString(R.string.try_named, getString(info.name))
            setOnClickListener {
                startActivity(
                    Intent(this@ExercisesActivity, InterceptActivity::class.java)
                        .putExtra(InterceptActivity.EXTRA_PREVIEW, true)
                        .putExtra(InterceptActivity.EXTRA_KIND, kind)
                        .putExtra(InterceptActivity.EXTRA_PKG, prefs.targets.firstOrNull() ?: packageName)
                )
            }
        }
        Ui.checkRow(group, getString(info.name), getString(info.desc), kind in prefs.exercises, tryIt) { on ->
            val now = prefs.exercises
            if (!on && now.size <= 1) {
                Toast.makeText(this, R.string.exercises_min, Toast.LENGTH_SHORT).show()
                false
            } else {
                prefs.exercises = if (on) now + kind else now - kind
                true
            }
        }
    }
}
