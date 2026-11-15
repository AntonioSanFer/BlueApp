package com.blueapp.detox

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

/**
 * Watches which app comes to the front and puts the pause screen in front of guarded apps.
 * The rules live in [SessionTracker]; this class only feeds it events and acts on decisions.
 *
 * Battery design:
 *  - Purely event driven. The system wakes us only when a window changes; there is no
 *    polling, no foreground service, no wakelock, no alarms and no network.
 *  - Each event costs a hash-set lookup on the main thread and nothing else.
 *  - The only timers are two Handler callbacks that exist while a session is running.
 *    Handler time pauses in deep sleep, so the expiry is also re-checked against the wall
 *    clock whenever the screen is unlocked or an app comes to the front.
 */
class GuardService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val tracker = SessionTracker(GRACE_MS)

    /** Windows that don't mean "you switched apps": us, the keyboard, the status bar, system dialogs. */
    private val ignored = HashSet<String>()

    /** Last real app in front. */
    private var foreground: String? = null
    private var lastLaunch = 0L

    private val expire = Runnable {
        act(tracker.onExpired(now(), InterceptActivity.isShowing), force = true)
    }
    private val warn = Runnable {
        if (tracker.inSessionApp) Toast.makeText(this, R.string.one_minute_left, Toast.LENGTH_SHORT).show()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                // Screen off counts as leaving: come back after the grace period and you get a fresh pause.
                Intent.ACTION_SCREEN_OFF -> tracker.onScreenOff(now())
                // After unlock the app underneath may not send a window event, so re-check.
                Intent.ACTION_USER_PRESENT -> {
                    refreshIgnored() // picks up a newly installed keyboard
                    foreground?.let { evaluate(it) }
                }
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        prefs = Prefs(this)
        tracker.targets = prefs.targets
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        refreshIgnored()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored) return
        foreground = pkg
        evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        val hadSession = tracker.sessionPkg != null
        act(tracker.onForeground(pkg, now(), InterceptActivity.isShowing))
        if (hadSession && tracker.sessionPkg == null) cancelTimers()
    }

    private fun act(d: SessionTracker.Decision, force: Boolean = false) {
        if (d !is SessionTracker.Decision.Pause) return
        cancelTimers()
        val t = now()
        if (!force && t - lastLaunch < 1200) return // debounce bursts of window events
        lastLaunch = t
        startActivity(
            Intent(this, InterceptActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(InterceptActivity.EXTRA_PKG, d.pkg)
                .putExtra(InterceptActivity.EXTRA_TIME_UP, d.timeUp)
                .putExtra(InterceptActivity.EXTRA_USED_MS, d.usedMs)
        )
    }

    /** Called by the pause screen once you've earned a session. */
    fun startSession(pkg: String, minutes: Int) {
        cancelTimers()
        tracker.start(pkg, minutes, now())
        foreground = pkg
        handler.postDelayed(expire, minutes * 60_000L)
        if (minutes >= 2) handler.postDelayed(warn, (minutes - 1) * 60_000L)
    }

    /** The pause screen closed without a decision (e.g. switched via recents). Re-check what's in front. */
    fun recheckSoon() {
        handler.postDelayed({
            lastLaunch = 0L
            foreground?.let { evaluate(it) }
        }, 400)
    }

    private fun cancelTimers() {
        handler.removeCallbacks(expire)
        handler.removeCallbacks(warn)
    }

    private fun refreshIgnored() {
        ignored.clear()
        ignored += packageName
        ignored += "com.android.systemui"
        ignored += "android" // share sheets, system dialogs shown on top of the current app
        runCatching {
            getSystemService(InputMethodManager::class.java)
                .enabledInputMethodList.forEach { ignored += it.packageName }
        }
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == Prefs.K_TARGETS) {
            tracker.targets = prefs.targets
            if (tracker.sessionPkg == null) cancelTimers()
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (instance !== this) return
        instance = null
        cancelTimers()
        tracker.end()
        runCatching { unregisterReceiver(screenReceiver) }
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        /** Leave and come back within this window and your session keeps going. */
        const val GRACE_MS = 60_000L

        @Volatile
        var instance: GuardService? = null
            private set
    }
}
