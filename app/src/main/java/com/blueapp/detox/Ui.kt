package com.blueapp.detox

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    /** A row of equal-width minute chips; the chosen one is filled. */
    fun minuteChips(row: LinearLayout, current: Int, onPick: (Int) -> Unit) {
        val ctx = row.context
        row.removeAllViews()
        Prefs.SESSION_CHOICES.forEachIndexed { i, m ->
            val on = m == current
            val chip = TextView(ctx).apply {
                text = m.toString()
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(ctx.attrColor(if (on) R.attr.onAccentColor else R.attr.textMain))
                background = GradientDrawable().apply {
                    cornerRadius = ctx.dp(14).toFloat()
                    setColor(ctx.attrColor(if (on) R.attr.accentColor else R.attr.surface2Color))
                    setStroke(ctx.dp(1), ctx.attrColor(if (on) R.attr.accentColor else R.attr.outlineColor))
                }
                minHeight = ctx.dp(48)
                contentDescription = ctx.resources.getQuantityString(R.plurals.minutes, m, m)
                isSelected = on
                setOnClickListener {
                    onPick(m)
                    minuteChips(row, m, onPick)
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = ctx.dp(8)
            })
        }
    }

    /** Equal-width segmented choice; the selected option is filled with the primary color. */
    fun segmented(row: LinearLayout, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
        val ctx = row.context
        row.removeAllViews()
        options.forEachIndexed { i, (id, label) ->
            val on = id == current
            val seg = TextView(ctx).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(ctx.attrColor(if (on) R.attr.onAccentColor else R.attr.textMain))
                background = GradientDrawable().apply {
                    cornerRadius = ctx.dp(14).toFloat()
                    setColor(ctx.attrColor(if (on) R.attr.accentColor else R.attr.surface2Color))
                    setStroke(ctx.dp(1), ctx.attrColor(if (on) R.attr.accentColor else R.attr.outlineColor))
                }
                minHeight = ctx.dp(48)
                setPadding(ctx.dp(6), 0, ctx.dp(6), 0)
                isSelected = on
                setOnClickListener {
                    onPick(id)
                    segmented(row, options, id, onPick)
                }
            }
            row.addView(seg, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = ctx.dp(8)
            })
        }
    }

    fun openAccessibilitySettings(a: Activity) {
        a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** Android 13+ blocks accessibility for sideloaded apps until "Allow restricted settings" is tapped here. */
    fun openAppInfo(a: Activity) {
        a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
    }

    fun openOverlaySettings(a: Activity) {
        try {
            a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))
        } catch (_: ActivityNotFoundException) {
            openAppInfo(a)
        }
    }

    fun canOverlay(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun appSummary(ctx: Context, pkgs: Set<String>): String =
        if (pkgs.isEmpty()) ctx.getString(R.string.no_apps)
        else pkgs.map { InterceptActivity.labelOf(ctx, it) }.sorted().joinToString(", ")
}
