package io.github.ninetailsflip.settings

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarLineTest {
    private fun texts(s: BarState) = BarLine.marks(s).map { it.text }

    @Test
    fun `the marks in order - new, modes, radios, battery`() {
        val s = BarState(
            time = "8:32", unread = 3, alarm = true, dnd = true, ringer = Ringer.VIBRATE,
            bluetooth = Link.ON, wifi = Link.CONNECTED, signal = 3, battery = 100,
        )
        assertEquals(listOf("✉3", BarLine.ALARM, "DND", "vib", "BT", "Wi-Fi", "▂▄▆", "100 %"), texts(s))
    }

    @Test
    fun `nothing to say says only the battery`() {
        assertEquals(listOf("57 %"), texts(BarState(battery = 57)))
        assertEquals(emptyList<String>(), texts(BarState()))
    }

    @Test
    fun `a connected radio is bright, one only on is dim`() {
        val marks = BarLine.marks(BarState(bluetooth = Link.CONNECTED, wifi = Link.ON))
        assertEquals(Tone.BRIGHT, marks.first { it.text == "BT" }.tone)
        assertEquals(Tone.DIM, marks.first { it.text == "Wi-Fi" }.tone)
    }

    @Test
    fun `the signal, no service, no SIM, airplane mode`() {
        assertEquals("▂▄▆█", BarLine.signal(4))
        assertEquals("▁", BarLine.signal(0))
        assertEquals("×", BarLine.signal(-1))
        assertFalse(texts(BarState(signal = null, battery = 50)).any { it.startsWith("▂") || it == "×" })
        assertEquals(listOf("✈"), texts(BarState(airplane = true, signal = 2)))
    }

    @Test
    fun `charging, and a low battery`() {
        assertEquals("80 %+", BarLine.marks(BarState(battery = 80, charging = true)).single().text)
        assertEquals(Tone.ALERT, BarLine.marks(BarState(battery = 9)).single().tone)
        assertEquals(Tone.BRIGHT, BarLine.marks(BarState(battery = 9, charging = true)).single().tone)
    }

    @Test
    fun `with too little room the least important marks go first`() {
        val marks = BarLine.marks(BarState(unread = 2, alarm = true, ringer = Ringer.SILENT, wifi = Link.CONNECTED, signal = 2, battery = 40))
        // Monospace: a character each, a space between.
        val width = { m: List<Mark> -> (m.sumOf { it.text.length } + (m.size - 1).coerceAtLeast(0)).toFloat() }
        assertEquals(marks, BarLine.fit(marks, 100f, width))
        val fitted = BarLine.fit(marks, 20f, width).map { it.text }
        assertEquals(listOf("✉2", "Wi-Fi", "▂▄", "40 %"), fitted)
        assertEquals(listOf("40 %"), BarLine.fit(marks, 4f, width).map { it.text })
    }
}

class HeadsUpRulesTest {
    private fun posted(
        importance: Int = NotificationManager.IMPORTANCE_HIGH, ongoing: Boolean = false, summary: Boolean = false,
        media: Boolean = false, alertOnce: Boolean = false, seen: Boolean = false, own: Boolean = false,
    ) = HeadsUpRules.Posted(importance, ongoing, summary, media, alertOnce, seen, own)

    @Test
    fun `an urgent new message shows`() = assertTrue(HeadsUpRules.shows(posted()))

    @Test
    fun `what SystemUI wouldn't pop up doesn't`() {
        assertFalse(HeadsUpRules.shows(posted(importance = NotificationManager.IMPORTANCE_DEFAULT)))
        assertFalse(HeadsUpRules.shows(posted(ongoing = true)))
        assertFalse(HeadsUpRules.shows(posted(summary = true)))
        assertFalse(HeadsUpRules.shows(posted(media = true)))
        assertFalse(HeadsUpRules.shows(posted(own = true)))
    }

    @Test
    fun `alert once - the first time only`() {
        assertTrue(HeadsUpRules.shows(posted(alertOnce = true)))
        assertFalse(HeadsUpRules.shows(posted(alertOnce = true, seen = true)))
        assertTrue(HeadsUpRules.shows(posted(seen = true)))
    }

    @Test
    fun `the line`() {
        assertEquals("Mom: are you coming?", HeadsUpRules.line("Messages", "Mom", "are you coming?"))
        assertEquals("Clock: Alarm", HeadsUpRules.line("Clock", "Alarm", null))
        assertEquals("Messages: two\nlines".replace('\n', ' '), HeadsUpRules.line("Messages", "", "two\nlines"))
        assertEquals("Messages", HeadsUpRules.line("Messages", null, " "))
    }
}
