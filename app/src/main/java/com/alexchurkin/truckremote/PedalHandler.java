package com.alexchurkin.truckremote;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Touch logic of a pedal.
 * Digital mode: pedal is fully pressed while it is touched.
 * Analog mode: touch starts with {@link #START_LEVEL}, vertical drag changes the level.
 * Lock (gas only): horizontal swipe while holding keeps the current level after release;
 * the next touch of the pedal unlocks it (and continues from the locked level).
 */
public class PedalHandler {

    public interface Listener {
        void onPedalChanged(PedalHandler pedal);

        void onPedalLockChanged(PedalHandler pedal, boolean locked);
    }

    private static final float START_LEVEL = 0.5f;
    //Part of the pedal area height that changes the level from 0 to 1
    private static final float TRAVEL_HEIGHT_PART = 0.6f;

    private final Listener listener;
    private final float lockDistancePx;
    private boolean analog, lockAllowed;

    private boolean touched, locked;
    private float level;
    private float downX, downY, downLevel, travelPx;

    public PedalHandler(Listener listener, float lockDistancePx) {
        this.listener = listener;
        this.lockDistancePx = lockDistancePx;
    }

    public void configure(boolean analog, boolean lockAllowed) {
        this.analog = analog;
        this.lockAllowed = lockAllowed;
        if (!lockAllowed) unlock();
    }

    public void onDown(float x, float y, int viewHeight) {
        boolean wasLocked = locked;
        if (locked) {
            locked = false;
            listener.onPedalLockChanged(this, false);
        }
        touched = true;
        downX = x;
        downY = y;
        travelPx = max(1f, viewHeight * TRAVEL_HEIGHT_PART);
        downLevel = analog ? (wasLocked ? level : START_LEVEL) : 1f;
        level = downLevel;
        listener.onPedalChanged(this);
    }

    public void onMove(float x, float y) {
        if (!touched || locked) return;

        if (analog) {
            float newLevel = clamp(downLevel + (downY - y) / travelPx);
            if (newLevel != level) {
                level = newLevel;
                listener.onPedalChanged(this);
            }
        }

        if (lockAllowed && level > 0 && abs(x - downX) > lockDistancePx) {
            locked = true;
            listener.onPedalLockChanged(this, true);
        }
    }

    public void onUp() {
        if (!touched) return;
        touched = false;
        if (!locked) level = 0;
        listener.onPedalChanged(this);
    }

    //Releases the pedal completely (including lock)
    public void release() {
        boolean wasLocked = locked;
        boolean wasActive = isActive();
        touched = false;
        locked = false;
        level = 0;
        if (wasLocked) listener.onPedalLockChanged(this, false);
        if (wasActive) listener.onPedalChanged(this);
    }

    public void unlock() {
        if (!locked) return;
        locked = false;
        if (!touched) level = 0;
        listener.onPedalLockChanged(this, false);
        listener.onPedalChanged(this);
    }

    public boolean isActive() {
        return touched || locked;
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isAnalog() {
        return analog;
    }

    //From 0 to 1
    public float getLevel() {
        return isActive() ? level : 0f;
    }

    private static float clamp(float value) {
        return max(0f, min(1f, value));
    }
}
