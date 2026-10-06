package com.alexchurkin.truckremote.data.settings

import com.alexchurkin.truckremote.data.controller.ControllerAction

/**
 * What is in each place of the quick actions panel: [PAGES] pages of [SLOTS] places, null is an empty place.
 * Every action is on the panel at most once.
 */
data class ActionLayout(val pages: List<List<ControllerAction?>>) {

    init {
        require(pages.size == PAGES && pages.all { it.size == SLOTS })
    }

    /**
     * Puts [action] (null: nothing) into the place; if the action is already on the panel,
     * the two places are swapped, so nothing is lost and nothing is doubled.
     */
    fun with(page: Int, slot: Int, action: ControllerAction?): ActionLayout {
        val slots = pages.map { it.toMutableList() }
        val replaced = slots[page][slot]
        if (action != null) {
            slots.forEach { pageSlots ->
                val index = pageSlots.indexOf(action)
                if (index >= 0) pageSlots[index] = replaced
            }
        }
        slots[page][slot] = action
        return ActionLayout(slots)
    }

    /**
     * Moves the action of the place [from] to the place [to] (places are counted through all pages) as icons are
     * moved in a launcher: the actions between [to] and the nearest empty place make room by shifting one place
     * towards it. The place the action has left is empty too, so a move inside a full page only shifts the actions
     * between the two places; an empty place of the page of [to] is taken before the ones of other pages.
     */
    fun moved(from: Int, to: Int): ActionLayout {
        val slots = pages.flatten().toMutableList()
        val action = slots.getOrNull(from)
        if (action == null || to !in slots.indices || from == to) return this
        slots[from] = null
        if (slots[to] != null) {
            val page = to / SLOTS * SLOTS until (to / SLOTS + 1) * SLOTS
            val hole = nearestEmpty(slots, to, from, page)
                ?: nearestEmpty(slots, to, from, slots.indices)
                ?: return this
            if (hole < to) {
                for (index in hole until to) slots[index] = slots[index + 1]
            } else {
                for (index in hole downTo to + 1) slots[index] = slots[index - 1]
            }
        }
        slots[to] = action
        return ActionLayout(slots.chunked(SLOTS))
    }

    // The empty place of [range] nearest to [to]; of two equally near ones the one on the side of [from]
    private fun nearestEmpty(slots: List<ControllerAction?>, to: Int, from: Int, range: IntRange): Int? {
        for (distance in 1..slots.size) {
            val sides = if (from < to) listOf(to - distance, to + distance) else listOf(to + distance, to - distance)
            sides.firstOrNull { it in range && slots[it] == null }?.let { return it }
        }
        return null
    }

    // Action codes by pages: "1,2,3,4,5,6,7,8;9,10,...", 0 is an empty place
    fun encode(): String = pages.joinToString(PAGE_SEPARATOR) { page ->
        page.joinToString(SLOT_SEPARATOR) { (it?.code ?: EMPTY).toString() }
    }

    companion object {
        const val PAGES = 6
        const val SLOTS = 8
        private const val PAGE_SEPARATOR = ";"
        private const val SLOT_SEPARATOR = ","
        private const val EMPTY = 0

        // Truck, driving, view; then the actions added later: cameras, looking around and gears, the rest
        val Default = ActionLayout(
            listOf(
                listOf(
                    ControllerAction.Engine,
                    ControllerAction.Trailer,
                    ControllerAction.Activate,
                    ControllerAction.LightHorn,
                    ControllerAction.Wipers,
                    ControllerAction.Beacon,
                    ControllerAction.DiffLock,
                    ControllerAction.LiftAxle,
                ),
                listOf(
                    ControllerAction.RetarderUp,
                    ControllerAction.RetarderDown,
                    ControllerAction.EngineBrake,
                    ControllerAction.QuickPark,
                    ControllerAction.CruiseUp,
                    ControllerAction.CruiseDown,
                    ControllerAction.CruiseResume,
                    null,
                ),
                listOf(
                    ControllerAction.CameraInterior,
                    ControllerAction.CameraChase,
                    ControllerAction.CameraCycle,
                    ControllerAction.Map,
                    ControllerAction.Display,
                    ControllerAction.Hud,
                    ControllerAction.RadioNext,
                    ControllerAction.QuickSave,
                ),
                listOf(
                    ControllerAction.Mirrors,
                    ControllerAction.CameraTop,
                    ControllerAction.CameraRoof,
                    ControllerAction.CameraLeanOut,
                    ControllerAction.CameraBumper,
                    ControllerAction.CameraWheel,
                    ControllerAction.CameraDriveBy,
                    ControllerAction.Screenshot,
                ),
                listOf(
                    ControllerAction.LookLeft,
                    ControllerAction.LookRight,
                    ControllerAction.GearUp,
                    ControllerAction.GearDown,
                    ControllerAction.RadioPrevious,
                    ControllerAction.Radio,
                    ControllerAction.AdvisorZoom,
                    ControllerAction.AdvisorMode,
                ),
                listOf(
                    ControllerAction.RoadAssistance,
                    ControllerAction.Menu,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                ),
            ),
        )

        /**
         * The default layout if the value is broken; unknown (e.g. removed) and repeated actions become empty places.
         * A layout saved when the panel had fewer pages keeps them, and the pages added since then come after them
         * as they are in the default layout (without the actions the saved pages already have).
         */
        fun decode(value: String?): ActionLayout {
            val saved = value?.split(PAGE_SEPARATOR)?.map { it.split(SLOT_SEPARATOR) }
            if (saved == null || saved.size !in 1..PAGES || saved.any { it.size != SLOTS }) return Default
            val placed = mutableSetOf<ControllerAction>()
            val pages = saved.map { page ->
                page.map { code ->
                    ControllerAction.entries.firstOrNull { it.code == code.trim().toIntOrNull() }
                        ?.takeIf { placed.add(it) }
                }
            }
            val added = Default.pages.drop(pages.size).map { page -> page.map { it?.takeIf(placed::add) } }
            return ActionLayout(pages + added)
        }
    }
}
