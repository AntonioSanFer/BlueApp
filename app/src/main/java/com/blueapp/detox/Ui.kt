package com.blueapp.detox

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object Ui {

    // ---------- Lists in a Group card (settings style) ----------

    /** Hairline between rows, inset from the left like system settings. */
    fun divider(ctx: Context) = View(ctx).apply {
        setBackgroundColor(ctx.attrColor(R.attr.outlineColor))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            marginStart = ctx.dp(16)
        }
    }

    /** A tappable row: label on the left, optional value and a chevron on the right. Returns the value view. */
    fun navRow(group: LinearLayout, label: String, value: String?, onClick: () -> Unit): TextView {
        val ctx = group.context
        if (group.childCount > 0) group.addView(divider(ctx))
        val valueView = TextView(ctx).apply {
            text = value
            textSize = 14f
            setTextColor(ctx.attrColor(R.attr.textDim))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ctx.dp(52)
            setPadding(ctx.dp(16), 0, ctx.dp(12), 0)
            background = ctx.getDrawable(R.drawable.row_bg)
            isClickable = true
            setOnClickListener { onClick() }
            addView(TextView(ctx).apply {
                text = label
                textSize = 15f
                setTextColor(ctx.attrColor(R.attr.textMain))
            })
            addView(valueView, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ctx.dp(16) })
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_chevron)
                imageTintList = ColorStateList.valueOf(ctx.attrColor(R.attr.textDim))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(ctx.dp(18), ctx.dp(18)).apply { marginStart = ctx.dp(6) })
        }
        group.addView(row, LinearLayout.LayoutParams(-1, -2))
        return valueView
    }

    /** A row with a title, optional subtitle and a checkbox; tapping anywhere toggles it. */
    fun checkRow(
        group: LinearLayout, title: String, sub: String?, checked: Boolean,
        trailing: View? = null, onToggle: (Boolean) -> Boolean,
    ): CheckBox {
        val ctx = group.context
        if (group.childCount > 0) group.addView(divider(ctx))
        val box = CheckBox(ctx).apply { isChecked = checked; isClickable = false; isFocusable = false }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply { text = title; textSize = 15f; setTextColor(ctx.attrColor(R.attr.textMain)) })
            if (sub != null) addView(TextView(ctx).apply { text = sub; textSize = 13f; setTextColor(ctx.attrColor(R.attr.textDim)) })
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ctx.dp(52)
            setPadding(ctx.dp(16), 0, ctx.dp(8), 0)
            background = ctx.getDrawable(R.drawable.row_bg)
            setOnClickListener { if (onToggle(!box.isChecked)) box.isChecked = !box.isChecked }
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            if (trailing != null) addView(trailing)
            addView(box)
        }
        group.addView(row, LinearLayout.LayoutParams(-1, -2))
        return box
    }

    /**
     * The "Time options" picker: which minute buttons appear after a pause.
     * At least one, at most [Prefs.MAX_TIME_OPTIONS]; saved on every change.
     */
    fun timeOptionsList(group: LinearLayout, prefs: Prefs, onChange: () -> Unit = {}) {
        val ctx = group.context
        group.removeAllViews()
        val chosen = prefs.timeOptions.toMutableSet()
        Prefs.TIME_CHOICES.forEach { m ->
            checkRow(group, ctx.resources.getQuantityString(R.plurals.minutes, m, m), null, m in chosen) { on ->
                when {
                    on && chosen.size >= Prefs.MAX_TIME_OPTIONS -> {
                        Toast.makeText(ctx, ctx.getString(R.string.time_options_max, Prefs.MAX_TIME_OPTIONS), Toast.LENGTH_SHORT).show()
                        false
                    }
                    !on && chosen.size <= 1 -> {
                        Toast.makeText(ctx, R.string.time_options_min, Toast.LENGTH_SHORT).show()
                        false
                    }
                    else -> {
                        if (on) chosen += m else chosen -= m
                        prefs.timeOptions = chosen.toList()
                        onChange()
                        true
                    }
                }
            }
        }
    }

    /**
     * The exercise blocks as checkable rows: on = every exercise in it, off = none.
     * The last row opens the per-exercise screen. Rebuilt by the caller when things change.
     */
    fun exerciseBlocks(group: LinearLayout, prefs: Prefs, onChange: () -> Unit) {
        val ctx = group.context
        group.removeAllViews()
        val on = prefs.exercises
        Exercises.BLOCKS.forEach { (block, kinds) ->
            val count = kinds.count { it in on }
            val sub = ctx.getString(Challenge.blockDesc(block)) +
                if (count in 1 until kinds.size) " · " + ctx.getString(R.string.taps_progress, count, kinds.size) else ""
            checkRow(group, ctx.getString(Challenge.blockName(block)), sub, count > 0) { want ->
                val next = if (want) on + kinds else on - kinds.toSet()
                if (next.isEmpty()) {
                    Toast.makeText(ctx, R.string.exercises_min, Toast.LENGTH_SHORT).show()
                    false
                } else {
                    prefs.exercises = next
                    group.post { exerciseBlocks(group, prefs, onChange); onChange() }
                    true
                }
            }
        }
        navRow(group, ctx.getString(R.string.exercises_choose),
            ctx.getString(R.string.taps_progress, on.size, Exercises.ALL.size)) {
            ctx.startActivity(Intent(ctx, ExercisesActivity::class.java))
        }
    }

    fun timeOptionsSummary(ctx: Context, options: List<Int>) =
        ctx.getString(R.string.time_options_summary, options.joinToString(", "))

    // ---------- System settings ----------

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
