package com.blueapp.detox

/**
 * Pure session rules, no Android types, so they can be unit tested.
 *
 *  - Opening a guarded app with no session -> pause.
 *  - After a pause you get one session for that app, up to the chosen minutes.
 *  - Hopping to another app (or turning the screen off) and coming back within [graceMs]
 *    keeps the session; staying away longer ends it.
 *  - Time running out while you're in the app -> "time's up" pause.
 *  - Opening a different guarded app ends the current session and pauses again.
 */
class SessionTracker(var graceMs: Long = 60_000L) {

    sealed interface Decision {
        data object None : Decision
        data class Pause(val pkg: String, val timeUp: Boolean, val usedMs: Long) : Decision
    }

    var targets: Set<String> = emptySet()
        set(v) {
            field = v
            if (sessionPkg != null && sessionPkg !in v) end()
        }

    var sessionPkg: String? = null
        private set
    var sessionEnd = 0L
        private set
    private var sessionStart = 0L
    /** When we left the session app; 0 while in it. */
    private var leftAt = 0L

    val inSessionApp get() = sessionPkg != null && leftAt == 0L

    fun onForeground(pkg: String, now: Long, pauseShowing: Boolean): Decision {
        if (pkg !in targets) {
            if (sessionPkg != null && leftAt == 0L) leftAt = now
            return Decision.None
        }
        if (pauseShowing) return Decision.None

        if (pkg == sessionPkg) {
            val away = if (leftAt == 0L) 0L else now - leftAt
            if (now < sessionEnd && away < graceMs) {
                leftAt = 0L
                return Decision.None
            }
            val timeUp = now >= sessionEnd && away < graceMs
            val used = minOf(sessionEnd, now) - sessionStart
            end()
            return Decision.Pause(pkg, timeUp, if (timeUp) used else 0L)
        }

        end()
        return Decision.Pause(pkg, false, 0L)
    }

    fun onScreenOff(now: Long) {
        if (sessionPkg != null && leftAt == 0L) leftAt = now
    }

    fun start(pkg: String, minutes: Int, now: Long) {
        sessionPkg = pkg
        sessionStart = now
        sessionEnd = now + minutes * 60_000L
        leftAt = 0L
    }

    /** The session timer fired. */
    fun onExpired(now: Long, pauseShowing: Boolean): Decision {
        val pkg = sessionPkg ?: return Decision.None
        val stillThere = leftAt == 0L && !pauseShowing
        val used = now - sessionStart
        end()
        return if (stillThere) Decision.Pause(pkg, true, used) else Decision.None
    }

    fun end() {
        sessionPkg = null
        sessionEnd = 0L
        sessionStart = 0L
        leftAt = 0L
    }

    companion object {
        /**
         * Buttons on "How long do you need?": your own time options, or after time ran out,
         * short extensions (1, 2, 5 min) capped at your longest option.
         */
        fun options(timeOptions: List<Int>, extension: Boolean): List<Int> {
            val max = timeOptions.maxOrNull() ?: 1
            if (extension) return listOf(1, 2, 5).filter { it <= max }.ifEmpty { listOf(max) }
            return timeOptions.distinct().sorted().ifEmpty { listOf(max) }
        }
    }
}
