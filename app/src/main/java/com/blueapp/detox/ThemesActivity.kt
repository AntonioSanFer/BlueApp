package com.blueapp.detox

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Theme menu. Each card is painted in its own theme's colors, so what you see is what you get.
 * Picking one applies it immediately to every screen, including the pause screen.
 */
class ThemesActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_themes)
        findViewById<Button>(R.id.done).setOnClickListener { finish() }
        build()
    }

    private fun build() {
        val list = findViewById<LinearLayout>(R.id.list)
        list.removeAllViews()
        val selected = Themes.current(prefs).id
        Themes.ALL.forEachIndexed { i, t ->
            list.addView(card(t, t.id == selected), LinearLayout.LayoutParams(-1, -2).apply {
                if (i > 0) topMargin = dp(12)
            })
        }
    }

    private fun card(t: AppTheme, isSelected: Boolean): View {
        val c = ContextThemeWrapper(this, t.style)
        val accent = c.attrColor(R.attr.accentColor)
        val outline = c.attrColor(R.attr.outlineColor)

        val card = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(c.attrColor(R.attr.bgColor))
                setStroke(dp(if (isSelected) 2 else 1), if (isSelected) accent else outline)
            }
            alpha = if (t.available) 1f else 0.5f
            isClickable = t.available
            contentDescription = getString(t.name)
            if (t.available) setOnClickListener {
                prefs.themeId = t.id
                recreate() // repaint this screen in the new theme; others follow on resume
            }
        }

        // Swatches: background, surface, primary, secondary.
        val swatches = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(R.attr.surfaceColor, R.attr.surface2Color, R.attr.accentColor, R.attr.accent2Color)
            .forEachIndexed { i, attr ->
                swatches.addView(View(c).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c.attrColor(attr))
                        setStroke(dp(1), outline)
                    }
                }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { if (i > 0) marginStart = -dp(6) })
            }
        card.addView(swatches)

        val texts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(c).apply {
            setText(t.name)
            textSize = 17f
            setTextColor(c.attrColor(R.attr.textMain))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        })
        texts.addView(TextView(c).apply {
            text = if (t.available) getString(t.desc) else getString(R.string.theme_needs_android12)
            textSize = 13f
            setTextColor(c.attrColor(R.attr.textDim))
        })
        card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })

        if (isSelected) {
            card.addView(TextView(c).apply {
                text = "✓"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(c.attrColor(R.attr.onAccentColor))
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(8) })
        }
        return card
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
