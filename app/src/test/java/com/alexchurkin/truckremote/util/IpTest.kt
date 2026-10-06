package com.alexchurkin.truckremote.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpTest {

    @Test
    fun `valid addresses`() {
        listOf("192.168.1.10", "10.0.0.1", "255.255.255.255", "0.0.0.0").forEach { assertTrue(it, isValidIpv4(it)) }
    }

    @Test
    fun `invalid addresses`() {
        listOf("", "192.168.1", "192.168.1.256", "1.2.3.4.5", "a.b.c.d", "1..2.3", "-1.2.3.4", "1234.1.1.1")
            .forEach { assertFalse(it, isValidIpv4(it)) }
    }
}
