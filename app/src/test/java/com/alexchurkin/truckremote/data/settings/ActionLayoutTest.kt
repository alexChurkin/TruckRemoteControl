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
    fun `moved action shifts the ones between its old and new places`() {
        // Page 0 is full: Engine, Trailer, Activate, LightHorn, Wipers, Beacon, DiffLock, LiftAxle
        val forward = ActionLayout.Default.moved(from = 0, to = 2)
        assertEquals(
            listOf(
                ControllerAction.Trailer,
                ControllerAction.Activate,
                ControllerAction.Engine,
                ControllerAction.LightHorn,
            ),
            forward.pages[0].take(4),
        )
        assertEquals(ActionLayout.Default.pages[0].drop(4), forward.pages[0].drop(4))

        val back = ActionLayout.Default.moved(from = 3, to = 1)
        assertEquals(
            listOf(
                ControllerAction.Engine,
                ControllerAction.LightHorn,
                ControllerAction.Trailer,
                ControllerAction.Activate,
            ),
            back.pages[0].take(4),
        )
        assertEquals(ActionLayout.Default.pages[1], back.pages[1])
    }

    @Test
    fun `moved action takes an empty place without shifting anything`() {
        // The last place of page 1 is empty
        val layout = ActionLayout.Default.moved(from = 0, to = 15)

        assertNull(layout.pages[0][0])
        assertEquals(ControllerAction.Engine, layout.pages[1][7])
        assertEquals(ActionLayout.Default.pages[0].drop(1), layout.pages[0].drop(1))
    }

    @Test
    fun `action moved to another page shifts its actions to their empty place`() {
        // Engine goes to the first place of page 1: its actions move towards the empty last place
        val layout = ActionLayout.Default.moved(from = 0, to = 8)

        assertNull(layout.pages[0][0])
        assertEquals(ControllerAction.Engine, layout.pages[1][0])
        assertEquals(ControllerAction.RetarderUp, layout.pages[1][1])
        assertEquals(ControllerAction.CruiseResume, layout.pages[1][7])
        assertEquals(ActionLayout.Default.pages[2], layout.pages[2])
    }

    @Test
    fun `action moved to a full page pushes one of its actions to the next empty place`() {
        // Page 2 is full, the nearest empty place is the one Map leaves... on the same page
        val inside = ActionLayout.Default.moved(from = 19, to = 16)
        assertEquals(ControllerAction.Map, inside.pages[2][0])
        assertEquals(ControllerAction.CameraInterior, inside.pages[2][1])
        assertEquals(ControllerAction.CameraCycle, inside.pages[2][3])

        // RetarderUp of page 1 goes to the full page 0: the place it has left is the nearest empty one
        val across = ActionLayout.Default.moved(from = 8, to = 7)
        assertEquals(ControllerAction.RetarderUp, across.pages[0][7])
        assertEquals(ControllerAction.LiftAxle, across.pages[1][0])
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
        assertEquals(ActionLayout.Default, ActionLayout.Default.moved(from = 15, to = 0))
        assertEquals(ActionLayout.Default, ActionLayout.Default.moved(from = 0, to = 99))
        assertEquals(ActionLayout.Default, ActionLayout.Default.moved(from = 3, to = 3))
    }

    @Test
    fun `layout saved with fewer pages gets the pages added since then`() {
        // Three pages as they were saved by the previous version, the engine moved to the last place of page 1
        val old = ActionLayout.Default.pages.take(3).joinToString(";") { page ->
            page.joinToString(",") { (it?.code ?: 0).toString() }
        }
        val saved = old.replaceFirst("1,", "0,").replaceFirst(",0;", ",1;")

        val layout = ActionLayout.decode(saved)

        assertNull(layout.pages[0][0])
        assertEquals(ControllerAction.Engine, layout.pages[1][7])
        assertEquals(ActionLayout.Default.pages.drop(3), layout.pages.drop(3))
        assertEquals(
            ActionLayout.Default,
            ActionLayout.decode(
                ActionLayout.Default.pages.take(3).joinToString(";") { page ->
                    page.joinToString(",") {
                        (
                            it?.code
                                ?: 0
                            ).toString()
                    }
                },
            ),
        )
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
}
