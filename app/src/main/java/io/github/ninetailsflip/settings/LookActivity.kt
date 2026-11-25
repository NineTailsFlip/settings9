package io.github.ninetailsflip.settings

import life.furrer.nine.core.NineSurface
import life.furrer.nine.core.settings.NineSettingsActivity
import life.furrer.nine.core.settings.SettingsScreen

/** Settings' own look (font, hint bar), backup and about - the page every Nine app has, from
 *  the `*` menu. The phone's settings themselves are [ScreenActivity]. */
class LookActivity : NineSettingsActivity() {
    override fun buildAppSections(screen: SettingsScreen, surface: NineSurface) {}
}
