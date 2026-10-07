package com.alexchurkin.truckremote.ui.shortcuts

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.data.settings.AppMode
import com.alexchurkin.truckremote.ui.dashboard.DashboardActivity
import com.alexchurkin.truckremote.ui.guide.GuideActivity
import com.alexchurkin.truckremote.ui.settings.SettingsActivity

/**
 * Shortcuts of the launcher icon (a long press on it). Set from the code, not from a static XML: their intents
 * name the package, which differs in the debug build.
 */
object AppShortcuts {

    private enum class Shortcut(
        @param:StringRes val label: Int,
        @param:DrawableRes val icon: Int,
        val screen: Class<*>,
    ) {
        Settings(R.string.settings, R.drawable.ic_shortcut_settings, SettingsActivity::class.java),
        Dashboard(R.string.dashboard_mode, R.drawable.ic_shortcut_dashboard, DashboardActivity::class.java),
        Guide(R.string.guide, R.drawable.ic_shortcut_guide, GuideActivity::class.java),
    }

    // A device in the dashboard mode opens the dashboard from the icon anyway
    fun update(context: Context, mode: AppMode?) {
        val shortcuts = Shortcut.entries
            .filter { it != Shortcut.Dashboard || mode == AppMode.Controller }
            .map { shortcut ->
                ShortcutInfoCompat.Builder(context, shortcut.name)
                    .setShortLabel(context.getString(shortcut.label))
                    .setIcon(IconCompat.createWithResource(context, shortcut.icon))
                    .setIntent(Intent(context, shortcut.screen).setAction(Intent.ACTION_VIEW))
                    .build()
            }
        // Some launchers have no shortcuts or limit them: they are only a convenience
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }
}
