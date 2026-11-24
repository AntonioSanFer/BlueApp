package com.blueapp.detox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExercisesTest {
    private val rnd = Random(42)

    @Test fun everyExerciseIsInExactlyOneBlock() {
        assertEquals(Exercises.ALL.size, Exercises.ALL.toSet().size)
        Exercises.ALL.forEach { assertTrue(it, Exercises.blockOf(it) != null) }
    }

    @Test fun onlyPicksEnabledAndNeverRepeats() {
        val on = setOf("math", "colors", "words")
        var last: String? = null
        repeat(500) {
            val k = Exercises.pick(on, last, rnd)
            assertTrue(k in on)
            assertNotEquals(last, k)
            last = k
        }
    }

    @Test fun singleExerciseRepeats() {
        // "Timer only" from the old switch: just the countdown, every time.
        repeat(20) { assertEquals("countdown", Exercises.pick(setOf("countdown"), "countdown", rnd)) }
    }

    @Test fun emptyOrUnknownFallsBackToEverything() {
        assertTrue(Exercises.pick(emptySet(), null, rnd) in Exercises.ALL)
        assertTrue(Exercises.pick(setOf("gone"), null, rnd) in Exercises.ALL)
    }

    @Test fun blocksGetEqualShareRegardlessOfSize() {
        // Calm has 5 on, Memory only 1: Memory should still come up about half the time.
        val on = Exercises.kindsOf(Exercises.CALM).toSet() + "words"
        val n = 4000
        val words = (1..n).count { Exercises.pick(on, null, rnd) == "words" }
        assertTrue("words picked $words of $n", words in (n * 0.42).toInt()..(n * 0.58).toInt())
    }

    @Test fun everyBlockShowsUp() {
        val seen = (1..300).map { Exercises.blockOf(Exercises.pick(Exercises.ALL.toSet(), null, rnd)) }.toSet()
        assertEquals(setOf(Exercises.CALM, Exercises.AGILITY, Exercises.MEMORY), seen)
    }

    @Test fun optInExercisesAreInTheCatalogue() {
        assertTrue(Exercises.OPT_IN.all { it in Exercises.ALL })
        assertEquals(Exercises.AGILITY, Exercises.blockOf("equations"))
    }
}
