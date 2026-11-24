package com.blueapp.detox

import kotlin.random.Random

/**
 * The exercise catalogue, grouped in blocks. Pure Kotlin so the picking rules are unit-tested.
 *
 * A pause first picks one of your enabled blocks at random, then an exercise inside it, so a
 * block with more exercises doesn't crowd out the others. Never the same exercise twice in a row.
 */
object Exercises {
    const val CALM = "calm"
    const val AGILITY = "agility"
    const val MEMORY = "memory"

    val BLOCKS: List<Pair<String, List<String>>> = listOf(
        CALM to listOf("countdown", "box", "senses", "hold", "phrase"),
        AGILITY to listOf("math", "equations", "colors", "taps", "count"),
        MEMORY to listOf("digits", "sequence", "words", "reverse"),
    )

    val ALL: List<String> = BLOCKS.flatMap { it.second }

    /** Harder extras that start off; you turn them on yourself. */
    val OPT_IN: Set<String> = setOf("equations")

    fun kindsOf(block: String): List<String> = BLOCKS.firstOrNull { it.first == block }?.second.orEmpty()

    fun blockOf(kind: String): String? = BLOCKS.firstOrNull { kind in it.second }?.first

    /** Next exercise from [enabled]: random block, then random exercise in it, avoiding [lastKind]. */
    fun pick(enabled: Set<String>, lastKind: String?, rnd: Random = Random.Default): String {
        val on = ALL.filter { it in enabled }.ifEmpty { ALL }
        val fresh = on.filter { it != lastKind }.ifEmpty { return on.first() }
        val blocks = BLOCKS.map { (_, kinds) -> kinds.filter { it in fresh } }.filter { it.isNotEmpty() }
        return blocks.random(rnd).random(rnd)
    }
}
