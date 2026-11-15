package com.blueapp.detox

import com.blueapp.detox.SessionTracker.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionTrackerTest {
    private val tiktok = "com.zhiliaoapp.musically"
    private val insta = "com.instagram.android"
    private val chat = "com.whatsapp"
    private val home = "com.android.launcher"
    private val min = 60_000L

    private lateinit var t: SessionTracker

    @Before
    fun setUp() {
        t = SessionTracker(graceMs = 60_000L)
        t.targets = setOf(tiktok, insta)
    }

    private fun pause(pkg: String, timeUp: Boolean = false) = Decision.Pause(pkg, timeUp, 0L)

    @Test fun nonTargetIsIgnored() {
        assertEquals(Decision.None, t.onForeground(chat, 0, false))
    }

    @Test fun openingTargetWithoutSessionPauses() {
        assertEquals(pause(tiktok), t.onForeground(tiktok, 0, false))
    }

    @Test fun noDoublePauseWhileShowing() {
        assertEquals(Decision.None, t.onForeground(tiktok, 0, pauseShowing = true))
    }

    @Test fun sessionAllowsUseUntilEnd() {
        t.start(tiktok, 10, 0)
        assertEquals(Decision.None, t.onForeground(tiktok, 1_000, false))
        assertEquals(Decision.None, t.onForeground(tiktok, 9 * min, false))
    }

    @Test fun quickHopKeepsSession() {
        t.start(tiktok, 10, 0)
        t.onForeground(chat, 2 * min, false)
        assertEquals(Decision.None, t.onForeground(tiktok, 2 * min + 30_000, false))
        assertEquals(tiktok, t.sessionPkg)
    }

    @Test fun longAbsenceEndsSession() {
        t.start(tiktok, 10, 0)
        t.onForeground(home, 2 * min, false)
        assertEquals(pause(tiktok), t.onForeground(tiktok, 4 * min, false))
        assertNull(t.sessionPkg)
    }

    @Test fun screenOffCountsAsLeaving() {
        t.start(tiktok, 10, 0)
        t.onScreenOff(1 * min)
        assertEquals(pause(tiktok), t.onForeground(tiktok, 5 * min, false))
    }

    @Test fun shortScreenOffKeepsSession() {
        t.start(tiktok, 10, 0)
        t.onScreenOff(1 * min)
        assertEquals(Decision.None, t.onForeground(tiktok, 1 * min + 20_000, false))
    }

    @Test fun pastEndWhileInAppIsTimeUp() {
        // Handler was delayed by deep sleep; the next event notices the wall clock.
        t.start(tiktok, 5, 0)
        val d = t.onForeground(tiktok, 6 * min, false)
        assertEquals(Decision.Pause(tiktok, true, 5 * min), d)
    }

    @Test fun expiryWhileInAppIsTimeUp() {
        t.start(tiktok, 5, 0)
        assertEquals(Decision.Pause(tiktok, true, 5 * min), t.onExpired(5 * min, false))
        assertNull(t.sessionPkg)
    }

    @Test fun expiryWhileAwayJustEnds() {
        t.start(tiktok, 5, 0)
        t.onForeground(home, 4 * min, false)
        assertEquals(Decision.None, t.onExpired(5 * min, false))
        assertEquals(pause(tiktok), t.onForeground(tiktok, 5 * min + 10_000, false))
    }

    @Test fun sessionIsPerApp() {
        t.start(tiktok, 10, 0)
        assertEquals(pause(insta), t.onForeground(insta, 1 * min, false))
        assertNull(t.sessionPkg) // switching guarded apps burns the TikTok session
    }

    @Test fun removingTargetEndsItsSession() {
        t.start(tiktok, 10, 0)
        t.targets = setOf(insta)
        assertNull(t.sessionPkg)
        assertEquals(Decision.None, t.onForeground(tiktok, 1 * min, false))
    }

    @Test fun durationOptions() {
        assertEquals(listOf(2, 5, 10), SessionTracker.options(10, false))
        assertEquals(listOf(1, 2, 5), SessionTracker.options(5, false))
        assertEquals(listOf(10, 20, 45), SessionTracker.options(45, false))
        assertEquals(listOf(3, 5, 15), SessionTracker.options(15, false))
        assertEquals(listOf(1, 2, 5), SessionTracker.options(30, true))
        assertTrue(SessionTracker.options(30, false).all { it in 1..30 })
    }

    @Test fun scatterStaysInBoundsWithoutOverlap() {
        repeat(50) {
            val d = 56
            val pts = scatter(900, 800, d, 8)
            assertEquals(8, pts.size)
            pts.forEach { (x, y) -> assertTrue(x in 0..(900 - d) && y in 0..(800 - d)) }
            for (i in pts.indices) for (j in i + 1 until pts.size) {
                val (ax, ay) = pts[i]; val (bx, by) = pts[j]
                assertTrue(kotlin.math.abs(ax - bx) >= d || kotlin.math.abs(ay - by) >= d)
            }
        }
    }
}
