package com.alexchurkin.truckremote.net

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingClientTest {

    @Test
    fun `reconnection delay grows up to 5 seconds`() {
        val delays = (0..6).map { TrackingClient.reconnectDelayMs(it) }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 5_000L, 5_000L, 5_000L, 5_000L), delays)
    }
}
