package com.alexchurkin.truckremote.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Language of the app. [System] follows the language of the device (default).
 * It is the per-app language of Android: on Android 13+ it is also shown in the system settings of the app,
 * on older versions AppCompat stores it (see AppLocalesMetadataHolderService in the manifest).
 */
enum class AppLanguage(val tag: String, val nativeName: String?) {
    System("", null),
    English("en", "English"),
    Russian("ru", "Русский"),
    Belarusian("be", "Беларуская"),
    Ukrainian("uk", "Українська"),
    ;

    fun apply() {
        AppCompatDelegate.setApplicationLocales(
            if (this == System) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }

    companion object {
        fun current(): AppLanguage {
            val language = AppCompatDelegate.getApplicationLocales()[0]?.language ?: return System
            return entries.firstOrNull { it != System && it.tag == language } ?: System
        }
    }
}
