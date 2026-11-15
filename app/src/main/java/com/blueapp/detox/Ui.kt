package com.blueapp.detox

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
        val d = ctx.resources.displayMetrics.density
        row.removeAllViews()
        Prefs.SESSION_CHOICES.forEachIndexed { i, m ->
            val on = m == current
            val chip = TextView(ctx).apply {
                text = m.toString()
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(if (on) Color.WHITE else ctx.getColor(R.color.text))
                background = GradientDrawable().apply {
                    cornerRadius = 14 * d
                    setColor(ctx.getColor(if (on) R.color.blue else R.color.surface_2))
                    setStroke((1 * d).toInt(), ctx.getColor(if (on) R.color.blue else R.color.outline))
                }
                minHeight = (48 * d).toInt()
                contentDescription = ctx.resources.getQuantityString(R.plurals.minutes, m, m)
                isSelected = on
                setOnClickListener {
                    onPick(m)
                    minuteChips(row, m, onPick)
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = (8 * d).toInt()
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
