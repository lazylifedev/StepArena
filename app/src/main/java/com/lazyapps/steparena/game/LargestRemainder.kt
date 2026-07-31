package com.lazyapps.steparena.game

/** Distributes a non-negative total by non-negative weights without losing units. */
internal fun largestRemainder(total: Long, weights: List<Long>): List<Long> {
    if (weights.isEmpty()) return emptyList()
    val safeTotal = total.coerceAtLeast(0)
    val safeWeights = weights.map { it.coerceAtLeast(0) }
    val denominator = safeWeights.sum().takeIf { it > 0 } ?: return List(weights.size) { 0L }
    val floors = safeWeights.map { safeTotal * it / denominator }
    var remaining = safeTotal - floors.sum()
    val order = safeWeights.indices.sortedWith(
        compareByDescending<Int> { (safeTotal * safeWeights[it]) % denominator }.thenBy { it },
    )
    val result = floors.toMutableList()
    for (index in order) {
        if (remaining == 0L) break
        result[index]++
        remaining--
    }
    return result
}
