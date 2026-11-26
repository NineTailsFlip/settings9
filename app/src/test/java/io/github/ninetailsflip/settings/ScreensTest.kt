package io.github.ninetailsflip.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import life.furrer.nine.core.ui.RowListView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ScreensTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun launch(intent: Intent): ScreenActivity =
        Robolectric.buildActivity(ScreenActivity::class.java, intent).setup().get()

    private fun texts(activity: ScreenActivity): List<String> {
        fun collect(v: View, out: MutableList<String>) {
            when (v) {
                is RowListView -> (0 until v.childCount).forEach { collect(v.getChildAt(it), out) }
                is android.widget.TextView -> out += v.text.toString()
                is ViewGroup -> (0 until v.childCount).forEach { collect(v.getChildAt(it), out) }
            }
        }
        return mutableListOf<String>().also { collect(activity.window.decorView, it) }
    }

    @Test
    fun `the main page lists the sections`() {
        val activity = launch(Intent(context, ScreenActivity::class.java))
        val shown = texts(activity)
        assertTrue(shown.contains("Settings"))
        assertTrue(shown.contains("Network & internet"))
        assertTrue(shown.contains("Bluetooth"))
    }

    @Test
    fun `every page opens without breaking`() {
        val names = listOf(
            Screens.NETWORK, Screens.WIFI, Screens.MOBILE, Screens.BLUETOOTH, Screens.APPS, Screens.DEFAULT_APPS,
            Screens.NOTIFICATIONS, Screens.SOUND, Screens.DISPLAY, Screens.BATTERY, Screens.STORAGE, Screens.SECURITY,
            Screens.LOCATION, Screens.ACCOUNTS, Screens.ACCESSIBILITY, Screens.SYSTEM, Screens.LANGUAGES,
            Screens.KEYBOARD, Screens.LAYOUTS, Screens.DATE_TIME, Screens.ZONES, Screens.DEVELOPER, Screens.RESET, Screens.ABOUT,
            Screens.OUTER, Screens.KEYS,
        )
        names.forEach { name ->
            val activity = launch(ScreenActivity.intentFor(context, name))
            assertFalse("$name broke", texts(activity).contains("This page didn't open"))
        }
        val app = launch(ScreenActivity.intentFor(context, Screens.APP, context.packageName))
        assertFalse(texts(app).contains("This page didn't open"))
    }

    @Test
    fun `android settings intents open the matching page`() {
        assertEquals(Screens.WIFI to null, Screens.forIntent(Intent(Settings.ACTION_WIFI_SETTINGS)))
        assertEquals(Screens.MAIN to null, Screens.forIntent(Intent("android.settings.SOMETHING_NEW")))
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.example"))
        assertEquals(Screens.APP to "com.example", Screens.forIntent(details))
        val notifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, "com.example")
        assertEquals(Screens.APP to "com.example", Screens.forIntent(notifications))
        assertEquals(Screens.APPS to null, Screens.forIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)))
    }

    @Test
    fun `OK on a section opens its page, and Back closes a page`() {
        val activity = launch(Intent(context, ScreenActivity::class.java))
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        val next = shadowOf(activity).nextStartedActivity
        assertEquals(Screens.NETWORK, next.getStringExtra(ScreenActivity.EXTRA_SCREEN))
        activity.onKeyDown(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `jumping skips headers and wraps`() {
        val keys = listOf(null, 'A', 'A', null, 'B', 'C')
        assertEquals(4, jumpByKey(keys, 1, 1))
        assertEquals(5, jumpByKey(keys, 4, 1))
        assertEquals(1, jumpByKey(keys, 5, 1))
        assertNull(jumpByKey(listOf<Char?>('A', 'A'), 0, 1))
    }

    @Test
    fun `a bar shows a value on its scale`() {
        assertEquals("▮▮▮▮▮▯▯▯▯▯  5 of 10", bar(5, 10))
        assertEquals("▯▯▯▯▯▯▯▯▯▯  0 of 15", bar(0, 15))
    }

    @Test
    fun `a PIN is 4 to 16 digits`() {
        assertTrue(Lock.isValidPin("1234"))
        assertFalse(Lock.isValidPin("123"))
        assertFalse(Lock.isValidPin("12a4"))
    }
}
