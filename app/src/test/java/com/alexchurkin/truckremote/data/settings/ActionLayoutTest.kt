package com.alexchurkin.truckremote.data.settings

import com.alexchurkin.truckremote.data.controller.ControllerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionLayoutTest {

    @Test
    fun `default layout survives encoding`() {
        assertEquals(ActionLayout.Default, ActionLayout.decode(ActionLayout.Default.encode()))
    }

    @Test
    fun `broken value gives the default layout`() {
        assertEquals(ActionLayout.Default, ActionLayout.decode(null))
        assertEquals(ActionLayout.Default, ActionLayout.decode(""))
        assertEquals(ActionLayout.Default, ActionLayout.decode("1,2,3;4,5"))
    }

    @Test
    fun `unknown and repeated actions become empty places`() {
        val layout = ActionLayout.decode("1,1,99,x,0,0,0,0;0,0,0,0,0,0,0,0;0,0,0,0,0,0,0,23")

        assertEquals(ControllerAction.Engine, layout.pages[0][0])
        assertNull(layout.pages[0][1])
        assertNull(layout.pages[0][2])
        assertNull(layout.pages[0][3])
        assertEquals(ControllerAction.QuickSave, layout.pages[2][7])
    }

    @Test
    fun `action from another place swaps the places`() {
        val layout = ActionLayout.Default.with(page = 0, slot = 0, action = ControllerAction.Map)

        assertEquals(ControllerAction.Map, layout.pages[0][0])
        assertEquals(ControllerAction.Engine, layout.pages[2][3])
    }

    @Test
    fun `action into an empty place leaves its old place empty`() {
        val layout = ActionLayout.Default.with(page = 1, slot = 7, action = ControllerAction.Engine)

        assertEquals(ControllerAction.Engine, layout.pages[1][7])
        assertNull(layout.pages[0][0])
    }

    @Test
    fun `place can be cleared`() {
        val layout = ActionLayout.Default.with(page = 2, slot = 1, action = null)

        assertNull(layout.pages[2][1])
        assertEquals(ControllerAction.CameraInterior, layout.pages[2][0])
    }
}
