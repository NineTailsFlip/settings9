package io.github.ninetailsflip.settings

import android.app.Application
import io.github.ninetailsflip.update.SetupChoices
import life.furrer.nine.core.KeyHelp
import life.furrer.nine.core.Nine
import life.furrer.nine.core.NineApp
import life.furrer.nine.core.NineSurface

class SettingsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Nine.init(
            NineApp(
                id = "settings",
                name = "Settings",
                tagline = "The phone's settings",
                surfaces = listOf(
                    NineSurface(
                        id = "",
                        name = "Settings",
                        introText = "The phone's settings, as Nine lists: network, Bluetooth, apps, sound, display, security, languages, keyboard and the rest.",
                        keyMap = listOf(
                            KeyHelp("Up / Down", "Move"),
                            KeyHelp("OK", "Open, choose, or flip a value"),
                            KeyHelp("Left / Right", "Change a value; jump by letter in long lists"),
                            KeyHelp("Back", "The previous page"),
                            KeyHelp("*", "Menu"),
                        ),
                    ),
                ),
                websiteUrl = "https://github.com/NineTailsFlip",
                sourceUrl = "https://github.com/NineTailsFlip/settings9",
                trackerUrl = "https://github.com/NineTailsFlip/settings9/issues",
                settingsActivity = LookActivity::class.java,
            ),
        )
        // The look chosen in the phone's first-time setup, like every Nine app.
        SetupChoices.adopt(this)
    }
}
