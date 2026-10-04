package com.alexchurkin.truckremote.net

import kotlin.math.abs

data class LinkQuality(
    // null: the server doesn't number its messages (older than 1.4), loss can't be measured
    val lossPercent: Int?,
    // Average deviation of the interval between server messages from the usual one
    val jitterMs: Int,
    // The longest silence in the window
    val maxGapMs: Int,
) {
    val isGood: Boolean
        get() = (lossPercent ?: 0) <= MAX_GOOD_LOSS_PERCENT && maxGapMs <= MAX_GOOD_GAP_MS

    private companion object {
        const val MAX_GOOD_LOSS_PERCENT = 3
        const val MAX_GOOD_GAP_MS = 150
    }
}

/**
 * Measures the link by the messages of the server, which are sent at a constant rate,
 * so nothing has to be added to the protocol except the sequence number.
 * Only the last [windowMs] are taken into account.
 */
class LinkQualityMeter(private val windowMs: Long = 2_000) {

    private class Arrival(val timeMs: Long, val sequence: Long?)

    private val arrivals = ArrayDeque<Arrival>()

    fun onMessage(timeMs: Long, sequence: Long?) {
        arrivals.addLast(Arrival(timeMs, sequence))
        dropOld(timeMs)
    }

    fun reset() = arrivals.clear()

    // null when there are too few messages to judge
    fun quality(nowMs: Long): LinkQuality? {
        dropOld(nowMs)
        if (arrivals.size < MIN_MESSAGES) return null

        val intervals = arrivals.zipWithNext { a, b -> b.timeMs - a.timeMs }
        val usual = intervals.sorted()[intervals.size / 2]
        val jitter = intervals.sumOf { abs(it - usual) } / intervals.size
        // Silence up to now counts too: it may be the beginning of a long gap
        val maxGap = maxOf(intervals.max(), nowMs - arrivals.last().timeMs)

        return LinkQuality(lossPercent(), jitter.toInt(), maxGap.toInt())
    }

    private fun lossPercent(): Int? {
        val sequences = arrivals.mapNotNull { it.sequence }
        if (sequences.size < arrivals.size) return null
        val expected = sequences.max() - sequences.min() + 1
        if (expected <= 0) return null
        val received = sequences.distinct().size
        return ((expected - received) * 100 / expected).toInt().coerceIn(0, 100)
    }

    private fun dropOld(nowMs: Long) {
        while (arrivals.isNotEmpty() && nowMs - arrivals.first().timeMs > windowMs) arrivals.removeFirst()
    }

    private companion object {
        const val MIN_MESSAGES = 5
    }
}
