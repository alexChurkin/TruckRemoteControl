package com.alexchurkin.scsremote;

public class GameButton {

    private boolean state = false;

    public GameButton() {
    }

    public void setPressed(boolean state) {
        this.state = state;
    }

    public boolean isPressed() {
        return state;
    }
}
