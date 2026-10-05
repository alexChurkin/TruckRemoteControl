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

    // Action codes by pages: "1,2,3,4,5,6,7,8;9,10,...", 0 is an empty place
    fun encode(): String = pages.joinToString(PAGE_SEPARATOR) { page ->
        page.joinToString(SLOT_SEPARATOR) { (it?.code ?: EMPTY).toString() }
    }

    companion object {
        const val PAGES = 3
        const val SLOTS = 8
        private const val PAGE_SEPARATOR = ";"
        private const val SLOT_SEPARATOR = ","
        private const val EMPTY = 0

        // Truck, driving, view
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
            ),
        )

        // The default layout if the value is broken; unknown (e.g. removed) and repeated actions become empty places
        fun decode(value: String?): ActionLayout {
            val pages = value?.split(PAGE_SEPARATOR)?.map { it.split(SLOT_SEPARATOR) }
            if (pages == null || pages.size != PAGES || pages.any { it.size != SLOTS }) return Default
            val placed = mutableSetOf<ControllerAction>()
            return ActionLayout(
                pages.map { page ->
                    page.map { code ->
                        ControllerAction.entries.firstOrNull { it.code == code.trim().toIntOrNull() }
                            ?.takeIf { placed.add(it) }
                    }
                },
            )
        }
    }
}
