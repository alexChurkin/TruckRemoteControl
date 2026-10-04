package com.alexchurkin.truckremote.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkQualityMeterTest {

    private val meter = LinkQualityMeter(windowMs = 2_000)

    @Test
    fun `steady messages are a good link`() {
        for (i in 0L until 50) meter.onMessage(timeMs = i * 20, sequence = i)
        val quality = meter.quality(nowMs = 49 * 20 + 5)!!

        assertEquals(0, quality.lossPercent)
        assertEquals(0, quality.jitterMs)
        assertEquals(20, quality.maxGapMs)
        assertTrue(quality.isGood)
    }

    @Test
    fun `lost messages are counted by sequence numbers`() {
        // Every 5th message is lost (the last one comes: losses after it can't be known yet)
        for (i in 0L until 50) if (i % 5 != 1L) meter.onMessage(timeMs = i * 20, sequence = i)
        val quality = meter.quality(nowMs = 980)!!

        assertEquals(20, quality.lossPercent)
        assertFalse(quality.isGood)
    }

    @Test
    fun `long silence makes the link bad even without loss`() {
        for (i in 0L until 20) meter.onMessage(timeMs = i * 20, sequence = i)
        meter.onMessage(timeMs = 700, sequence = 20)
        val quality = meter.quality(nowMs = 710)!!

        assertEquals(320, quality.maxGapMs)
        assertFalse(quality.isGood)
    }

    @Test
    fun `current silence is taken into account`() {
        for (i in 0L until 20) meter.onMessage(timeMs = i * 20, sequence = i)
        assertEquals(400 - 380, meter.quality(nowMs = 400)!!.maxGapMs)
        assertEquals(1_000 - 380, meter.quality(nowMs = 1_000)!!.maxGapMs)
    }

    @Test
    fun `jitter is the average deviation from the usual interval`() {
        // Intervals alternate 10 and 30 ms: the usual (median) one is 30, deviations are 20 and 0
        var time = 0L
        for (i in 0L until 21) {
            meter.onMessage(time, i)
            time += if (i % 2 == 0L) 10 else 30
        }
        val quality = meter.quality(nowMs = time)!!
        assertEquals(10, quality.jitterMs)
        assertEquals(0, quality.lossPercent)
    }

    @Test
    fun `old server without sequence numbers gives no loss`() {
        for (i in 0L until 10) meter.onMessage(timeMs = i * 20, sequence = null)
        assertNull(meter.quality(nowMs = 200)!!.lossPercent)
    }

    @Test
    fun `old messages leave the window and too few give no result`() {
        for (i in 0L until 10) meter.onMessage(timeMs = i * 20, sequence = i)
        assertNull(meter.quality(nowMs = 5_000))
        meter.onMessage(timeMs = 5_000, sequence = 10)
        assertNull(meter.quality(nowMs = 5_000))
    }
}
