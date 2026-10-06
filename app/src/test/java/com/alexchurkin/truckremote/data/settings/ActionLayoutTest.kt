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
        assertEquals(ControllerAction.EngineBrake, layout.pages[1][0])
    }

    @Test
    fun `action into an empty place leaves its old place empty`() {
        val layout = ActionLayout.Default.with(page = 4, slot = 7, action = ControllerAction.Engine)

        assertEquals(ControllerAction.Engine, layout.pages[4][7])
        assertNull(layout.pages[0][4])
    }

    @Test
    fun `moved action shifts the ones between its old and new places`() {
        // Page 0 is full: EngineBrake, RetarderDown, RetarderUp, Trailer, Engine, Activate, Wipers, LightHorn
        val forward = ActionLayout.Default.moved(from = 0, to = 2)
        assertEquals(
            listOf(
                ControllerAction.RetarderDown,
                ControllerAction.RetarderUp,
                ControllerAction.EngineBrake,
                ControllerAction.Trailer,
            ),
            forward.pages[0].take(4),
        )
        assertEquals(ActionLayout.Default.pages[0].drop(4), forward.pages[0].drop(4))

        val back = ActionLayout.Default.moved(from = 3, to = 1)
        assertEquals(
            listOf(
                ControllerAction.EngineBrake,
                ControllerAction.Trailer,
                ControllerAction.RetarderDown,
                ControllerAction.RetarderUp,
            ),
            back.pages[0].take(4),
        )
        assertEquals(ActionLayout.Default.pages[1], back.pages[1])
    }

    @Test
    fun `moved action takes an empty place without shifting anything`() {
        // The last place of page 4 is empty
        val layout = ActionLayout.Default.moved(from = 0, to = 39)

        assertNull(layout.pages[0][0])
        assertEquals(ControllerAction.EngineBrake, layout.pages[4][7])
        assertEquals(ActionLayout.Default.pages[0].drop(1), layout.pages[0].drop(1))
    }

    @Test
    fun `action moved to another page shifts its actions to their empty place`() {
        // The engine brake goes to the first place of page 4: its actions move towards the empty last place
        val layout = ActionLayout.Default.moved(from = 0, to = 32)

        assertNull(layout.pages[0][0])
        assertEquals(ControllerAction.EngineBrake, layout.pages[4][0])
        assertEquals(ControllerAction.RadioPrevious, layout.pages[4][1])
        assertEquals(ControllerAction.Menu, layout.pages[4][7])
        assertEquals(ActionLayout.Default.pages[3], layout.pages[3])
    }

    @Test
    fun `action moved to a full page pushes one of its actions to the next empty place`() {
        // Page 2 is full, the nearest empty place is the one the top camera leaves on the same page
        val inside = ActionLayout.Default.moved(from = 19, to = 16)
        assertEquals(ControllerAction.CameraTop, inside.pages[2][0])
        assertEquals(ControllerAction.CameraInterior, inside.pages[2][1])
        assertEquals(ControllerAction.CameraCycle, inside.pages[2][3])

        // Map of page 1 goes to the full page 0: the place it has left is the nearest empty one
        val across = ActionLayout.Default.moved(from = 8, to = 7)
        assertEquals(ControllerAction.Map, across.pages[0][7])
        assertEquals(ControllerAction.LightHorn, across.pages[1][0])
        assertEquals(ActionLayout.Default.pages[1].drop(1), across.pages[1].drop(1))
    }

    @Test
    fun `nothing is lost or doubled by a move, wrong places change nothing`() {
        val actions = ActionLayout.Default.pages.flatten().filterNotNull().toSet()
        for (from in 0 until ActionLayout.PAGES * ActionLayout.SLOTS) {
            for (to in 0 until ActionLayout.PAGES * ActionLayout.SLOTS) {
                val moved = ActionLayout.Default.moved(from, to).pages.flatten()
                assertEquals(actions, moved.filterNotNull().toSet())
                assertEquals(actions.size, moved.count { it != null })
            }
        }
        assertEquals(ActionLayout.Default, ActionLayout.Default.moved(from = 39, to = 0))
        assertEquals(ActionLayout.Default, ActionLayout.Default.moved(from = 0, to = 99))
        assertEquals(ActionLayout.Default, ActionLayout.Default.moved(from = 3, to = 3))
    }

    @Test
    fun `layout saved with fewer pages gets the pages added since then`() {
        // Three pages as they were saved by a previous version, the engine and the engine brake swapped
        val saved = ActionLayout.Default
            .with(page = 0, slot = 0, action = ControllerAction.Engine)
            .pages.take(3)
            .let(::encodePages)

        val layout = ActionLayout.decode(saved)

        assertEquals(ControllerAction.Engine, layout.pages[0][0])
        assertEquals(ControllerAction.EngineBrake, layout.pages[0][4])
        assertEquals(ActionLayout.Default.pages.drop(3), layout.pages.drop(3))
        assertEquals(ActionLayout.Default, ActionLayout.decode(encodePages(ActionLayout.Default.pages.take(3))))
    }

    @Test
    fun `actions missing from an older layout take the empty places of the added pages`() {
        // Gear up wasn't on the three pages of the previous version
        val saved = encodePages(ActionLayout.Default.with(page = 1, slot = 7, action = null).pages.take(3))

        val layout = ActionLayout.decode(saved)

        assertNull(layout.pages[1][7])
        assertEquals(ControllerAction.GearUp, layout.pages[4][7])
        assertEquals(ControllerAction.entries.size, layout.pages.flatten().count { it != null })
    }

    @Test
    fun `action taken off a layout of all pages stays off`() {
        val layout = ActionLayout.Default.with(page = 0, slot = 0, action = null)

        assertEquals(layout, ActionLayout.decode(layout.encode()))
    }

    @Test
    fun `every action is on the default layout once`() {
        val placed = ActionLayout.Default.pages.flatten().filterNotNull()

        assertEquals(ControllerAction.entries.toSet(), placed.toSet())
        assertEquals(ControllerAction.entries.size, placed.size)
    }

    @Test
    fun `place can be cleared`() {
        val layout = ActionLayout.Default.with(page = 2, slot = 1, action = null)

        assertNull(layout.pages[2][1])
        assertEquals(ControllerAction.CameraInterior, layout.pages[2][0])
    }

    private fun encodePages(pages: List<List<ControllerAction?>>) =
        pages.joinToString(";") { page -> page.joinToString(",") { (it?.code ?: 0).toString() } }
}
