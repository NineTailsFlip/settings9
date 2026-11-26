package io.github.ninetailsflip.settings

import android.app.Notification
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import life.furrer.nine.core.ui.RowListView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ShadeTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun texts(activity: ShadeActivity): List<String> {
        fun collect(v: View, out: MutableList<String>) {
            when (v) {
                is RowListView -> (0 until v.childCount).forEach { collect(v.getChildAt(it), out) }
                is android.widget.TextView -> out += v.text.toString()
                is ViewGroup -> (0 until v.childCount).forEach { collect(v.getChildAt(it), out) }
            }
        }
        return mutableListOf<String>().also { collect(activity.window.decorView, it) }
    }

    private fun shade(panel: String?) = Robolectric.buildActivity(
        ShadeActivity::class.java,
        Intent(context, ShadeActivity::class.java).putExtra(ShadeActivity.EXTRA_PANEL, panel),
    ).setup().get()

    @Test
    fun `the shade lists notifications and the quick settings`() {
        val shown = texts(shade("notifications"))
        assertFalse(shown.contains("This page didn't open"))
        assertTrue(shown.contains("NOTIFICATIONS"))
        assertTrue(shown.contains("QUICK SETTINGS"))
        assertTrue(shown.contains("Airplane mode"))
    }

    @Test
    fun `the settings panel opens on the first switch`() {
        val activity = shade("settings")
        // Wi-Fi comes first; on, it carries a tick.
        assertTrue(texts(activity).any { it == "Wi-Fi" || it == "✓ Wi-Fi" })
        assertTrue(texts(activity).contains("ok switch · * more · ‹ close"))
    }

    @Test
    fun `the same key again closes it`() {
        val controller = Robolectric.buildActivity(ShadeActivity::class.java, Intent(context, ShadeActivity::class.java)).setup()
        controller.newIntent(Intent(context, ShadeActivity::class.java).putExtra(ShadeActivity.EXTRA_TOGGLE, true))
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `Back closes it`() {
        val activity = shade(null)
        activity.onKeyDown(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        assertTrue(activity.isFinishing)
    }

    @Suppress("DEPRECATION")
    private fun sbn(id: Int, notification: Notification, overrideGroup: String? = null, tag: String? = null) =
        StatusBarNotification("android", "android", id, tag, 1000, 0, 0, notification, Process.myUserHandle(), id.toLong())
            .apply { overrideGroupKey = overrideGroup }

    @Test
    fun `the system's bundle of loose notifications hides behind them`() {
        // As on the phone: Android bundles its silent notifications under a summary with no text.
        val group = "Aggregate_SilentSection"
        val summary = Notification.Builder(context, "c").setGroup(group).setGroupSummary(true).build()
        val usb = Notification.Builder(context, "c").setContentTitle("Charging this device via USB").build()
        val adb = Notification.Builder(context, "c").setContentTitle("USB debugging connected").build()
        val posted = listOf(sbn(1, summary, group, "-1|android|g:$group"), sbn(32, usb, group), sbn(26, adb)).map(ShadeRules::of)
        assertEquals(listOf(posted[1].key, posted[2].key), ShadeRules.listed(posted)) // newest first, no summary
    }
}

class ShadeRulesTest {
    private fun posted(key: String, time: Long, group: String? = null, summary: Boolean = false, media: Boolean = false) =
        Posted(key, group, summary, media, time)

    @Test
    fun `newest first, the music left to its own row`() {
        val listed = ShadeRules.listed(listOf(posted("a", 1), posted("b", 3), posted("m", 5, media = true), posted("c", 2)))
        assertEquals(listOf("b", "c", "a"), listed)
    }

    @Test
    fun `a group shows its messages, its summary only when alone`() {
        val withChildren = listOf(posted("s", 9, group = "g", summary = true), posted("x", 1, group = "g"), posted("y", 2, group = "g"))
        assertEquals(listOf("y", "x"), ShadeRules.listed(withChildren))
        assertEquals(listOf("s"), ShadeRules.listed(listOf(posted("s", 9, group = "g", summary = true))))
    }

    @Test
    fun `how long ago`() {
        val now = 1_000_000_000_000L
        assertEquals("now", ShadeRules.ago(now, now - 30_000))
        assertEquals("5 min", ShadeRules.ago(now, now - 5 * 60_000))
        assertEquals("3 h", ShadeRules.ago(now, now - 3 * 3_600_000))
        assertTrue(ShadeRules.ago(now, now - 3 * 86_400_000L, Locale.US).matches(Regex("\\d+ \\w+")))
    }
}
