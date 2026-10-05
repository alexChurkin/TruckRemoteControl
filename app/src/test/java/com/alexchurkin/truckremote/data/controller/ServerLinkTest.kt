package com.alexchurkin.truckremote.data.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerLinkTest {

    @Test
    fun `link of the server window is parsed`() {
        assertEquals(ServerLink("192.168.1.10", 18250), ServerLink.parse("truckremote://192.168.1.10:18250"))
        assertEquals(ServerLink("10.0.0.5", 20000), ServerLink.parse(" TruckRemote://10.0.0.5:20000/ "))
    }

    @Test
    fun `other codes are rejected`() {
        assertNull(ServerLink.parse(null))
        assertNull(ServerLink.parse("https://example.com"))
        assertNull(ServerLink.parse("truckremote://192.168.1.10"))
        assertNull(ServerLink.parse("truckremote://192.168.1.300:18250"))
        assertNull(ServerLink.parse("truckremote://192.168.1.10:80"))
    }
}
