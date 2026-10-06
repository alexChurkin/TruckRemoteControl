package com.alexchurkin.truckremote.data.settings

import com.alexchurkin.truckremote.testing.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppModeTest {

    private val prefs = FakeSharedPreferences()
    private val settings = AppSettings(prefs)

    @Test
    fun `a new user is asked about the mode`() {
        assertNull(settings.appMode)

        settings.appMode = AppMode.Dashboard
        assertEquals(AppMode.Dashboard, settings.appMode)
    }

    @Test
    fun `who used the app before isn't asked`() {
        settings.guideShown = true
        assertEquals(AppMode.Controller, settings.appMode)

        // "Open at start" of the dashboard of the previous version
        prefs.edit().putBoolean("dashboardOnStart", true).apply()
        assertEquals(AppMode.Dashboard, settings.appMode)

        settings.appMode = AppMode.Controller
        assertEquals(AppMode.Controller, settings.appMode)
    }
}
