package com.blueapp.detox

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import java.lang.ref.WeakReference

/**
 * The pause screen. Shows a random challenge; once it's done you pick how long you need,
 * and the session timer starts. When time is up, this screen comes back.
 */
class InterceptActivity : ThemedActivity() {

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var host: FrameLayout
    private lateinit var leaveBtn: Button

    private var challenge: Challenge? = null
    private lateinit var pkg: String
    private lateinit var appLabel: String
    private var preview = false
    private var extension = false
    private var decided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme.applyStyle(R.style.Overlay_Intercept, true)
        current = WeakReference(this)
        setContentView(R.layout.activity_intercept)
        title = findViewById(R.id.title)
        subtitle = findViewById(R.id.subtitle)
        host = findViewById(R.id.host)
        leaveBtn = findViewById(R.id.leave)

        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: packageName
        appLabel = labelOf(this, pkg)

        findViewById<TextView>(R.id.app_name).text = getString(R.string.opening, appLabel)

        leaveBtn.text = getString(if (preview) R.string.close_preview else R.string.not_now)
        leaveBtn.setOnClickListener { leave() }

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { leave() }
        }

        // Only a fresh open counts; a recreate (theme or dark-mode switch) is the same pause.
        if (!preview && savedInstanceState == null) prefs.bump(Prefs.S_PAUSES)
        findViewById<TextView>(R.id.stats).text =
            if (preview) getString(R.string.preview_note) else getString(R.string.pause_stats, prefs.stat(Prefs.S_PAUSES))

        if (intent.getBooleanExtra(EXTRA_TIME_UP, false)) {
            showTimeUp(intent.getLongExtra(EXTRA_USED_MS, 0L))
        } else {
            // Previewing one exercise from Settings shows exactly that one.
            startChallenge(baseLevel(), if (preview) intent.getStringExtra(EXTRA_KIND) else null)
        }
    }

    /** More opens today -> slightly longer pauses. */
    private fun baseLevel(): Int = ((prefs.stat(Prefs.S_PAUSES) - 1) / 6).coerceIn(0, 2)

    private fun showTimeUp(usedMs: Long) {
        val minutes = ((usedMs + 30_000) / 60_000).coerceAtLeast(1)
        findViewById<TextView>(R.id.app_name).text = appLabel
        showHeading(getString(R.string.time_up), getString(R.string.time_up_sub, minutes, appLabel))
        leaveBtn.visibility = View.GONE
        findViewById<View>(R.id.stats).visibility = View.GONE

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val close = Button(this, null, 0, R.style.Primary).apply {
            text = getString(R.string.close_app, appLabel)
            setOnClickListener { leave(walkedAway = false) } // used the full session, not a resisted open
        }
        val more = Button(this, null, 0, R.style.Secondary).apply {
            text = getString(R.string.need_more)
            setOnClickListener {
                extension = true
                prefs.bump(Prefs.S_EXTENSIONS)
                leaveBtn.visibility = View.VISIBLE
                findViewById<View>(R.id.stats).visibility = View.VISIBLE
                // Each extra round today is a bit harder than the last.
                startChallenge((baseLevel() + prefs.stat(Prefs.S_EXTENSIONS)).coerceAtMost(3))
            }
        }
        col.addView(close, LinearLayout.LayoutParams(-1, -2))
        col.addView(more, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        col.addView(TextView(this).apply {
            setText(R.string.time_up_foot)
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(attrColor(R.attr.textDim))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        host.removeAllViews()
        host.addView(col, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun startChallenge(level: Int, kind: String? = null) {
        challenge?.dispose()
        val c = if (kind != null) Challenge.of(this, kind, level)
        else Challenge.random(this, level, prefs.lastKind, prefs.exercises)
        if (kind == null) prefs.lastKind = c.kind
        challenge = c
        showHeading(c.title, c.subtitle)
        c.mount(host) { onPassed() }
    }

    private fun onPassed() {
        challenge?.dispose()
        challenge = null
        host.removeAllViews()
        showHeading(resources.getStringArray(R.array.how_long).random(), getString(R.string.how_long_sub, appLabel))

        // Your time options from Settings, shuffled every time so there's no fixed spot
        // to tap without reading. Each row says when the session would end.
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.card)
            clipToOutline = true
        }
        val clock = android.text.format.DateFormat.getTimeFormat(this)
        val now = System.currentTimeMillis()
        SessionTracker.options(prefs.timeOptions, extension).shuffled().forEach { m ->
            if (group.childCount > 0) group.addView(Ui.divider(this))
            group.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                setPadding(dp(16), 0, dp(16), 0)
                background = getDrawable(R.drawable.row_bg)
                isClickable = true
                setOnClickListener { grant(m) }
                addView(TextView(context).apply {
                    text = resources.getQuantityString(R.plurals.minutes, m, m)
                    textSize = 16f
                    setTextColor(attrColor(R.attr.textMain))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(context).apply {
                    text = getString(R.string.until_time, clock.format(java.util.Date(now + m * 60_000L)))
                    textSize = 14f
                    setTextColor(attrColor(R.attr.textDim))
                })
            }, LinearLayout.LayoutParams(-1, -2))
        }
        host.addView(group, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
    }

    /** Title and subtitle above the content; hidden when empty (the countdown carries its own question). */
    private fun showHeading(t: String, sub: String) {
        title.text = t
        subtitle.text = sub
        title.visibility = if (t.isEmpty()) View.GONE else View.VISIBLE
        subtitle.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun grant(minutes: Int) {
        decided = true
        if (!preview) {
            GuardService.instance?.startSession(pkg, minutes)
            prefs.bump(Prefs.S_SESSIONS)
            prefs.bump(Prefs.S_MINUTES, minutes)
        }
        finish() // the app's own task is right underneath, exactly where you left it
    }

    private fun leave(walkedAway: Boolean = true) {
        decided = true
        if (!preview) {
            if (walkedAway) prefs.bump(Prefs.S_LEFT)
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    /** Back = walk away. Android 13+ goes through the OnBackInvokedCallback above. */
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Pre-Android 13 back handling")
    override fun onBackPressed() {
        leave()
    }

    override fun onDestroy() {
        challenge?.dispose()
        if (current?.get() === this) current = null
        // Swiped away via recents/gestures without choosing? Make sure the app underneath
        // doesn't get a free pass.
        if (!decided && !preview) GuardService.instance?.recheckSoon()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_TIME_UP = "time_up"
        const val EXTRA_USED_MS = "used_ms"
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_KIND = "kind"

        @Volatile
        private var current: WeakReference<InterceptActivity>? = null
        val isShowing get() = current?.get() != null

        fun labelOf(ctx: Context, pkg: String): String = runCatching {
            val pm = ctx.packageManager
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrDefault(Prefs.RECOMMENDED[pkg] ?: pkg)
    }
}
