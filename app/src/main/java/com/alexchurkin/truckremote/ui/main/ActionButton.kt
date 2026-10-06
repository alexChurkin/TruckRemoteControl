package com.alexchurkin.truckremote.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.data.controller.ControllerAction

data class ActionButton(
    val action: ControllerAction,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
)

/**
 * Label and icon of every action, for any place of the quick actions panel (see ActionLayout).
 * Actions are sent by their ids, so buttons can be moved between places and pages freely.
 */
val ActionButtons: Map<ControllerAction, ActionButton> = listOf(
    ActionButton(ControllerAction.Engine, R.string.action_engine, R.drawable.ic_action_engine),
    ActionButton(ControllerAction.Trailer, R.string.action_trailer, R.drawable.ic_action_trailer),
    ActionButton(ControllerAction.Activate, R.string.action_activate, R.drawable.ic_action_activate),
    ActionButton(ControllerAction.LightHorn, R.string.action_light_horn, R.drawable.ic_action_light_horn),
    ActionButton(ControllerAction.Wipers, R.string.action_wipers, R.drawable.ic_action_wipers),
    ActionButton(ControllerAction.Beacon, R.string.action_beacon, R.drawable.ic_action_beacon),
    ActionButton(ControllerAction.DiffLock, R.string.action_diff_lock, R.drawable.ic_action_diff_lock),
    ActionButton(ControllerAction.LiftAxle, R.string.action_lift_axle, R.drawable.ic_action_lift_axle),
    ActionButton(ControllerAction.RetarderUp, R.string.action_retarder_up, R.drawable.ic_action_retarder_up),
    ActionButton(ControllerAction.RetarderDown, R.string.action_retarder_down, R.drawable.ic_action_retarder_down),
    ActionButton(ControllerAction.EngineBrake, R.string.action_engine_brake, R.drawable.ic_action_engine_brake),
    ActionButton(ControllerAction.QuickPark, R.string.action_quick_park, R.drawable.ic_action_quick_park),
    ActionButton(ControllerAction.CruiseUp, R.string.action_cruise_up, R.drawable.ic_action_cruise_up),
    ActionButton(ControllerAction.CruiseDown, R.string.action_cruise_down, R.drawable.ic_action_cruise_down),
    ActionButton(ControllerAction.CruiseResume, R.string.action_cruise_resume, R.drawable.ic_action_cruise_resume),
    ActionButton(
        ControllerAction.CameraInterior,
        R.string.action_camera_interior,
        R.drawable.ic_action_camera_interior,
    ),
    ActionButton(ControllerAction.CameraChase, R.string.action_camera_chase, R.drawable.ic_action_camera_chase),
    ActionButton(ControllerAction.CameraCycle, R.string.action_camera_cycle, R.drawable.ic_action_camera_cycle),
    ActionButton(ControllerAction.Map, R.string.action_map, R.drawable.ic_action_map),
    ActionButton(ControllerAction.Display, R.string.action_display, R.drawable.ic_action_display),
    ActionButton(ControllerAction.Hud, R.string.action_hud, R.drawable.ic_action_hud),
    ActionButton(ControllerAction.RadioNext, R.string.action_radio_next, R.drawable.ic_action_radio_next),
    ActionButton(ControllerAction.QuickSave, R.string.action_quick_save, R.drawable.ic_action_quick_save),
    ActionButton(ControllerAction.Mirrors, R.string.action_mirrors, R.drawable.ic_action_mirrors),
    ActionButton(ControllerAction.CameraTop, R.string.action_camera_top, R.drawable.ic_action_camera_top),
    ActionButton(ControllerAction.CameraRoof, R.string.action_camera_roof, R.drawable.ic_action_camera_roof),
    ActionButton(
        ControllerAction.CameraLeanOut,
        R.string.action_camera_lean_out,
        R.drawable.ic_action_camera_lean_out,
    ),
    ActionButton(ControllerAction.CameraBumper, R.string.action_camera_bumper, R.drawable.ic_action_camera_bumper),
    ActionButton(ControllerAction.CameraWheel, R.string.action_camera_wheel, R.drawable.ic_action_camera_wheel),
    ActionButton(
        ControllerAction.CameraDriveBy,
        R.string.action_camera_drive_by,
        R.drawable.ic_action_camera_drive_by,
    ),
    ActionButton(ControllerAction.LookLeft, R.string.action_look_left, R.drawable.ic_action_look_left),
    ActionButton(ControllerAction.LookRight, R.string.action_look_right, R.drawable.ic_action_look_right),
    ActionButton(ControllerAction.GearUp, R.string.action_gear_up, R.drawable.ic_action_gear_up),
    ActionButton(ControllerAction.GearDown, R.string.action_gear_down, R.drawable.ic_action_gear_down),
    ActionButton(
        ControllerAction.RadioPrevious,
        R.string.action_radio_previous,
        R.drawable.ic_action_radio_previous,
    ),
    ActionButton(ControllerAction.Radio, R.string.action_radio, R.drawable.ic_action_radio),
    ActionButton(ControllerAction.AdvisorZoom, R.string.action_advisor_zoom, R.drawable.ic_action_advisor_zoom),
    ActionButton(ControllerAction.AdvisorMode, R.string.action_advisor_mode, R.drawable.ic_action_advisor_mode),
    ActionButton(
        ControllerAction.RoadAssistance,
        R.string.action_road_assistance,
        R.drawable.ic_action_road_assistance,
    ),
    ActionButton(ControllerAction.Screenshot, R.string.action_screenshot, R.drawable.ic_action_screenshot),
    ActionButton(ControllerAction.Menu, R.string.action_menu, R.drawable.ic_action_menu),
).associateBy { it.action }

fun ControllerAction.button(): ActionButton = ActionButtons.getValue(this)
