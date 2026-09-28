package io.github.zyrouge.symphony.utils

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

fun <T> List<T>.subListNonStrict(length: Int, start: Int = 0) =
    subList(start, min(start + length, size))

fun <T> List<T>.randomSubList(length: Int): List<T> {
    val mut = toMutableList()
    val out = mutableListOf<T>()
    val possibleLength = max(0, min(length, mut.size))
    for (i in 0 until possibleLength) {
        val index = Random.nextInt(mut.size)
        out.add(mut.removeAt(index))
    }
    return out
}

fun <T> List<T>.mutate(fn: MutableList<T>.() -> Unit): List<T> {
    val out = toMutableList()
    fn.invoke(out)
    return out
}

fun <T> concurrentListOf(): MutableList<T> = CopyOnWriteArrayList(mutableListOf<T>())

/**
 * Stable per-item keys for lazy lists that may contain duplicates: the id
 * itself for its first occurrence, `id#1`, `id#2`... after. Unlike an
 * index-based key, a key doesn't change when other items are inserted or
 * removed above it, which is what lets Modifier.animateItem() animate moves
 * and removals instead of treating every shifted row as new. Unlike a bare
 * id, duplicates (the same song twice in a queue) can't crash the list.
 */
fun List<String>.uniqueKeys(): List<String> {
    val seen = HashMap<String, Int>()
    return map { id ->
        val n = seen.merge(id, 1, Int::plus)!! - 1
        if (n == 0) id else "$id#$n"
    }
}
