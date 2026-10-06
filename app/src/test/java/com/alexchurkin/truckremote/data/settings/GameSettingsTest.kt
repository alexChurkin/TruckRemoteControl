package com.alexchurkin.truckremote.data.settings

import com.alexchurkin.truckremote.testing.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSettingsTest {

    private val settings = AppSettings(FakeSharedPreferences())

    @Test
    fun `both games share the settings by default`() {
        settings.game(Game.Ets2).steeringMaxAngle = 50

        assertEquals(50, settings.game(Game.Ats).steeringMaxAngle)
    }

    @Test
    fun `separate settings start as a copy and then differ`() {
        settings.game(Game.Ets2).steeringMaxAngle = 50
        settings.game(Game.Ets2).speedUnits = SpeedUnits.Metric

        settings.separateGameSettings = true
        assertEquals(50, settings.game(Game.Ats).steeringMaxAngle)
        assertEquals(SpeedUnits.Metric, settings.game(Game.Ats).speedUnits)

        settings.game(Game.Ats).steeringMaxAngle = 80
        assertEquals(50, settings.game(Game.Ets2).steeringMaxAngle)

        // The common settings are back when the switch is off
        settings.separateGameSettings = false
        assertEquals(50, settings.game(Game.Ats).steeringMaxAngle)
    }

    @Test
    fun `settings are copied from the other game`() {
        settings.separateGameSettings = true
        settings.game(Game.Ats).steeringSmoothness = 9

        settings.game(Game.Ets2).copyFrom(settings.game(Game.Ats))

        assertEquals(9, settings.game(Game.Ets2).steeringSmoothness)
    }

    @Test
    fun `units by game are miles only in ATS`() {
        assertTrue(SpeedUnits.ByGame.isImperial(Game.Ats))
        assertFalse(SpeedUnits.ByGame.isImperial(Game.Ets2))
        assertTrue(SpeedUnits.Imperial.isImperial(Game.Ets2))
        assertFalse(SpeedUnits.Metric.isImperial(Game.Ats))
        // What the game is set to goes first, a choice made in the app goes over it
        assertFalse(SpeedUnits.ByGame.isImperial(Game.Ats, inGame = false))
        assertTrue(SpeedUnits.ByGame.isImperial(Game.Ets2, inGame = true))
        assertTrue(SpeedUnits.Imperial.isImperial(Game.Ats, inGame = false))
    }
}
