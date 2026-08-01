package com.lazyapps.steparena.game

import java.math.BigInteger

data class CompetitiveDayAllocation(
    val total: Long,
    val eligible: Long,
    val restricted: Long,
    val excluded: Long,
)

private val ZERO = BigInteger.ZERO

/** Largest-remainder allocation with exact row and column margins. */
internal fun allocateCompetitiveDays(
    dayTotals: List<Long>,
    eligible: Long,
    restricted: Long,
    excluded: Long,
): List<CompetitiveDayAllocation> {
    val columns = dayTotals.map { it.coerceAtLeast(0) }
    val total = columns.fold(BigInteger.ZERO) { acc, value -> acc.add(value.toBigInteger()) }
    val rows = listOf(eligible, restricted, excluded).map { it.coerceAtLeast(0).toBigInteger() }
    val rowTotal = rows.fold(ZERO) { acc, value -> acc.add(value) }
    if (columns.isEmpty()) return emptyList()
    if (total == ZERO || rowTotal == ZERO) {
        return columns.map { CompetitiveDayAllocation(it, 0, 0, 0) }
    }

    val cells = Array(3) { row -> Array(columns.size) { column ->
        val numerator = rows[row].multiply(columns[column].toBigInteger())
        Cell(numerator.divide(total), numerator.mod(total), row, column)
    } }
    val result = Array(3) { row -> Array(columns.size) { column -> cells[row][column].floor } }
    val rowRemaining = rows.mapIndexed { row, expected ->
        expected.subtract((0 until columns.size).fold(ZERO) { acc, column -> acc.add(result[row][column]) })
    }.toMutableList()
    val columnRemaining = columns.mapIndexed { column, expected ->
        expected.toBigInteger().subtract((0 until 3).fold(ZERO) { acc, row -> acc.add(result[row][column]) })
    }.toMutableList()
    val candidates = cells.flatten().sortedWith(
        compareByDescending<Cell> { it.remainder }.thenBy { it.row }.thenBy { it.column },
    )
    var pending = rowRemaining.fold(ZERO) { acc, value -> acc.add(value) }
    while (pending > ZERO) {
        val cell = candidates.first { rowRemaining[it.row] > ZERO && columnRemaining[it.column] > ZERO }
        result[cell.row][cell.column] = result[cell.row][cell.column].add(BigInteger.ONE)
        rowRemaining[cell.row] = rowRemaining[cell.row].subtract(BigInteger.ONE)
        columnRemaining[cell.column] = columnRemaining[cell.column].subtract(BigInteger.ONE)
        pending = pending.subtract(BigInteger.ONE)
    }
    return columns.indices.map { column ->
        CompetitiveDayAllocation(
            columns[column], result[0][column].toLong(), result[1][column].toLong(), result[2][column].toLong(),
        )
    }
}

private data class Cell(val floor: BigInteger, val remainder: BigInteger, val row: Int, val column: Int)

/** Distributes a total by weights without multiplication overflow. */
internal fun largestRemainder(total: Long, weights: List<Long>): List<Long> {
    val safeTotal = total.coerceAtLeast(0).toBigInteger()
    val safeWeights = weights.map { it.coerceAtLeast(0).toBigInteger() }
    val denominator = safeWeights.fold(ZERO) { acc, value -> acc.add(value) }
    if (safeWeights.isEmpty() || denominator == ZERO) return List(weights.size) { 0L }
    val floors = safeWeights.map { safeTotal.multiply(it).divide(denominator) }
    var remaining = safeTotal.subtract(floors.fold(ZERO) { acc, value -> acc.add(value) }).toLong()
    val order = safeWeights.indices.sortedWith(
        compareByDescending<Int> { safeTotal.multiply(safeWeights[it]).mod(denominator) }.thenBy { it },
    )
    val result = floors.map { it.toLong() }.toMutableList()
    for (index in order) {
        if (remaining == 0L) break
        result[index]++
        remaining--
    }
    return result
}
