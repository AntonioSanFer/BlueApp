package com.blueapp.detox

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
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
    private lateinit var durations: LinearLayout
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
        durations = findViewById(R.id.durations)
        leaveBtn = findViewById(R.id.leave)

        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: packageName
        appLabel = labelOf(this, pkg)

        findViewById<TextView>(R.id.app_name).text = getString(R.string.opening, appLabel)
        runCatching { findViewById<ImageView>(R.id.app_icon).setImageDrawable(packageManager.getApplicationIcon(pkg)) }

        leaveBtn.text = getString(if (preview) R.string.close_preview else R.string.not_now)
        leaveBtn.setOnClickListener { leave() }

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { leave() }
        }

        // Only a fresh open counts; a recreate (theme or dark-mode switch) is the same pause.
        if (!preview && savedInstanceState == null) prefs.bump(Prefs.S_PAUSES)
        val pauses = prefs.stat(Prefs.S_PAUSES)
        val left = prefs.stat(Prefs.S_LEFT)
        findViewById<TextView>(R.id.stats).text =
            if (preview) getString(R.string.preview_note) else getString(R.string.pause_stats, pauses, left)

        if (intent.getBooleanExtra(EXTRA_TIME_UP, false)) {
            showTimeUp(intent.getLongExtra(EXTRA_USED_MS, 0L))
        } else {
            startChallenge(baseLevel())
        }
    }

    /** More opens today -> slightly longer pauses. */
    private fun baseLevel(): Int = ((prefs.stat(Prefs.S_PAUSES) - 1) / 6).coerceIn(0, 2)

    private fun showTimeUp(usedMs: Long) {
        val minutes = ((usedMs + 30_000) / 60_000).coerceAtLeast(1)
        title.text = getString(R.string.time_up)
        subtitle.text = getString(R.string.time_up_sub, minutes, appLabel)
        leaveBtn.visibility = View.GONE

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
                // Each extra round today is a bit harder than the last.
                startChallenge((baseLevel() + prefs.stat(Prefs.S_EXTENSIONS)).coerceAtMost(3))
            }
        }
        col.addView(close, LinearLayout.LayoutParams(-1, -2))
        col.addView(more, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        host.removeAllViews()
        host.addView(col)
    }

    private fun startChallenge(level: Int) {
        challenge?.dispose()
        val c = Challenge.random(this, level, prefs.lastKind)
        prefs.lastKind = c.kind
        challenge = c
        title.text = c.title
        subtitle.text = c.subtitle
        c.mount(host) { onPassed() }
    }

    private fun onPassed() {
        challenge?.dispose()
        challenge = null
        host.removeAllViews()
        title.text = resources.getStringArray(R.array.how_long).random()
        subtitle.text = getString(R.string.how_long_sub, appLabel)

        durations.removeAllViews()
        // Shuffled every time so there's no fixed spot to tap without reading.
        SessionTracker.options(prefs.maxSessionMin, extension).shuffled().forEachIndexed { i, m ->
            val b = Button(this, null, 0, R.style.Secondary).apply {
                text = resources.getQuantityString(R.plurals.minutes, m, m)
                setOnClickListener { grant(m) }
            }
            durations.addView(b, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(10) })
        }
        durations.visibility = View.VISIBLE
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

        @Volatile
        private var current: WeakReference<InterceptActivity>? = null
        val isShowing get() = current?.get() != null

        fun labelOf(ctx: Context, pkg: String): String = runCatching {
            val pm = ctx.packageManager
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrDefault(Prefs.RECOMMENDED[pkg] ?: pkg)
    }
}
