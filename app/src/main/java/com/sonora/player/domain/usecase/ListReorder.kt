package com.sonora.player.domain.usecase

/** Moves one element; out-of-range indexes leave the list unchanged. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val copy = toMutableList()
    val item = copy.removeAt(from)
    copy.add(to, item)
    return copy
}
