package com.alexchurkin.truckremote.billing

import com.alexchurkin.truckremote.billing.BillingManager.Companion.resultEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingManagerTest {

    @Test
    fun `silent check reports only changes`() {
        assertEquals(BillingEvent.Restored, resultEvent(owned = true, changed = true, reportNotFound = false))
        assertEquals(BillingEvent.Returned, resultEvent(owned = false, changed = true, reportNotFound = false))
        assertNull(resultEvent(owned = true, changed = false, reportNotFound = false))
        assertNull(resultEvent(owned = false, changed = false, reportNotFound = false))
    }

    @Test
    fun `requested restore always gives a result`() {
        assertEquals(BillingEvent.NotFound, resultEvent(owned = false, changed = false, reportNotFound = true))
        assertEquals(BillingEvent.Restored, resultEvent(owned = true, changed = false, reportNotFound = true))
        assertEquals(BillingEvent.Restored, resultEvent(owned = true, changed = true, reportNotFound = true))
    }
}
