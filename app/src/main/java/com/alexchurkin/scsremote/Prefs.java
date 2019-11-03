package com.alexchurkin.scsremote;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.Set;

public class Prefs {

    private static SharedPreferences prefs;

    public static void initialize(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static SharedPreferences get() {
        return prefs;
    }

    public static void putByte(String key, byte value) {
        getEditor().putInt(key, value).apply();
    }

    public static void putInt(String key, int value) {
        getEditor().putInt(key, value).apply();
    }

    public static void putLong(String key, long value) {
        getEditor().putLong(key, value).apply();
    }

    public static void putFloat(String key, float value) {
        getEditor().putFloat(key, value).apply();
    }

    public static void putBoolean(String key, boolean value) {
        getEditor().putBoolean(key, value).apply();
    }

    public static void putStringSet(String key, Set<String> strings) {
        getEditor().putStringSet(key, strings).apply();
    }


    public static byte getByte(String key, byte defValue) {
        return (byte) prefs.getInt(key, defValue);
    }

    public static int getInt(String key, int defValue) {
        return prefs.getInt(key, defValue);
    }

    public static long getLong(String key, long defValue) {
        return prefs.getLong(key, defValue);
    }

    public static float getFloat(String key, float defValue) {
        return prefs.getFloat(key, defValue);
    }

    public static boolean getBoolean(String key, boolean defValue) {
        return prefs.getBoolean(key, defValue);
    }

    public static Set<String> getStringSet(String key, Set<String> defValue) {
        return prefs.getStringSet(key, defValue);
    }

    private static SharedPreferences.Editor getEditor() {
        return prefs.edit();
    }
}