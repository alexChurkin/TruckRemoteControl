package com.alexchurkin.truckremote.ui.widget

// Where the pedal is hinged: the opposite edge goes away from the driver when the pedal is pressed
enum class PedalHinge {
    // Floor-mounted pedal (gas): the top goes down
    Bottom,

    // Hanging pedal (brake): the bottom goes down
    Top,
}
