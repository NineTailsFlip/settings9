package io.github.ninetailsflip.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import life.furrer.nine.core.ui.RowListView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** ThorSubScreen's provider, as Settings sees it: key, value, type rows; update writes. */
class FakeSubScreenProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor =
        MatrixCursor(arrayOf("key", "value", "type")).apply { stored.forEach { (k, v) -> addRow(arrayOf(k, v.toString(), "")) } }
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int {
        values?.keySet()?.forEach { stored[it] = values.get(it) }
        return values?.size() ?: 0
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        calls += method
        return null
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0

    companion object {
        val stored = mutableMapOf<String, Any?>()
        val calls = mutableListOf<String>()
    }
}

@RunWith(RobolectricTestRunner::class)
class OuterScreenTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun provider() {
        FakeSubScreenProvider.stored.clear()
        FakeSubScreenProvider.calls.clear()
        Robolectric.setupContentProvider(FakeSubScreenProvider::class.java, "org.thor2.subscreen.prefs")
    }

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

    private fun page(name: String, arg: String? = null) =
        Robolectric.buildActivity(ScreenActivity::class.java, ScreenActivity.intentFor(context, name, arg)).setup().get()

    @Test
    fun `the outer screen page shows its settings with the defaults`() {
        val shown = texts(page(Screens.OUTER))
        assertTrue(shown.contains("Outer screen"))
        assertTrue(shown.contains("Show it now"))
        assertTrue(shown.contains("CLOCK"))
    }

    @Test
    fun `switching it off writes to ThorSubScreen`() {
        val activity = page(Screens.OUTER)
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(false, FakeSubScreenProvider.stored["enabled"])
    }

    @Test
    fun `show it now asks ThorSubScreen to light the screen`() {
        val activity = page(Screens.OUTER)
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(listOf("show_now"), FakeSubScreenProvider.calls)
    }

    @Test
    fun `a key's action is chosen from the list`() {
        val activity = page(Screens.KEY_ACTION, "key_27_long")
        assertTrue(texts(activity).contains("✓ Flashlight"))
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals("camera", FakeSubScreenProvider.stored["key_27_long"])
    }
}
