package com.alexchurkin.scsremote;

public class ControllerButton {

    private boolean state = false;

    public ControllerButton() {
    }

    public void setPressed(boolean state) {
        this.state = state;
    }

    public boolean isPressed() {
        return state;
    }
}
