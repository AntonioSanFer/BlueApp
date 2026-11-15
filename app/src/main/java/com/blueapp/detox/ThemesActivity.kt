package com.blueapp.detox

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView

/** Settings › Theme: one radio list. Swatches show each theme's background and accent. */
class ThemesActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        findViewById<TextView>(R.id.title).setText(R.string.theme)
        findViewById<TextView>(R.id.footnote).apply { setText(R.string.theme_footnote); visibility = View.VISIBLE }

        val group = findViewById<LinearLayout>(R.id.group)
        val selected = Themes.current(prefs).id
        Themes.ALL.forEach { t ->
            if (group.childCount > 0) group.addView(Ui.divider(this))
            group.addView(row(t, t.id == selected), LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun row(t: AppTheme, isSelected: Boolean): View {
        val preview = ContextThemeWrapper(this, t.style)
        fun swatch(attr: Int) = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(preview.attrColor(attr))
                setStroke(dp(1), attrColor(R.attr.outlineColor))
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60)
            setPadding(dp(16), dp(8), dp(8), dp(8))
            background = getDrawable(R.drawable.row_bg)
            alpha = if (t.available) 1f else 0.45f
            isEnabled = t.available
            addView(swatch(R.attr.bgColor), LinearLayout.LayoutParams(dp(14), dp(28)))
            addView(swatch(R.attr.accentColor), LinearLayout.LayoutParams(dp(14), dp(28)).apply { marginStart = dp(4) })
            addView(LinearLayout(this@ThemesActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { setText(t.name); textSize = 15f; setTextColor(attrColor(R.attr.textMain)) })
                addView(TextView(context).apply {
                    text = getString(if (t.available) t.desc else R.string.theme_needs_android12)
                    textSize = 13f
                    setTextColor(attrColor(R.attr.textDim))
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
            addView(RadioButton(this@ThemesActivity).apply {
                isChecked = isSelected; isClickable = false; isFocusable = false
                contentDescription = getString(t.name)
            })
            if (t.available) setOnClickListener {
                prefs.themeId = t.id
                recreate() // repaint this screen; the others follow on resume
            }
        }
    }
}
