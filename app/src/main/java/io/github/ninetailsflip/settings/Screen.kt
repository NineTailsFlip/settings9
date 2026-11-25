package io.github.ninetailsflip.settings

import android.content.Context
import android.content.Intent

/** One row of a screen: a title over a dim line of details. [ok] runs on OK; [adjust] on Left and
 *  Right (-1 / +1), for a value changed in place. A [header] row names the rows under it and
 *  can't be selected; a row with neither [ok] nor [adjust] only informs. A [marked] row carries a
 *  tick (the choice in use, a layout that's on). */
class Row(
    val title: String,
    val meta: String = "",
    val marked: Boolean = false,
    val header: Boolean = false,
    val adjust: ((Int) -> Unit)? = null,
    val ok: (() -> Unit)? = null,
)

fun header(title: String) = Row(title, header = true)

enum class FieldKind { TEXT, PASSWORD, PIN }

/** A line of text to type under the list (a Wi-Fi password, a PIN, a name): OK hands it to
 *  [onDone]. [initial] fills the field to start with (renaming keeps the old name). */
class FieldRequest(val prompt: String, val kind: FieldKind, val initial: String = "", val onDone: (String) -> Unit)

/** The screen showing a [Screen]: [ScreenActivity]. */
interface ScreenHost {
    val context: Context
    /** Something changed: redraw the rows where they are. */
    fun refresh()
    fun ask(request: FieldRequest)
    /** A message in the hint bar until the next key. */
    fun say(text: String)
    /** Opens another screen on top of this one; Back returns here. */
    fun open(name: String, arg: String? = null)
    /** Starts another app's screen (a picker, a system dialog) and hands its result back. */
    fun startForResult(intent: Intent, onResult: (resultCode: Int, data: Intent?) -> Unit)
    fun close()
}

/**
 * A page of Settings: a title, a line of what it's for, and its rows. Every page is the same
 * Nine list - Up/Down move, OK opens or chooses, Left/Right change a value or jump through a long
 * list, `*` the menu, Back the previous page - so a page only says what its rows are and what
 * they do.
 */
abstract class Screen(val title: String) {
    lateinit var host: ScreenHost
    protected val context: Context get() = host.context

    open fun subtitle(): String = ""
    abstract fun rows(): List<Row>
    open fun hint(): String = "ok open · ‹ back · * menu"
    /** The row selected when the page opens. */
    open fun initialRow(): Int = rows().indexOfFirst { !it.header }.coerceAtLeast(0)
    /** The page became visible (opened, or returned to): start listening, reload. */
    open fun onShow() {}
    open fun onHide() {}
    /** Back inside the page (leaving a sub-list); false closes the page. */
    open fun back(): Boolean = false
    /** The page's own entries in the `*` menu. */
    open fun menu(): List<Pair<String, () -> Unit>> = emptyList()
    /** Left/Right on a row without [Row.adjust]: the row to jump to, or null. */
    open fun jump(from: Int, direction: Int): Int? = null

    protected fun say(text: String) = host.say(text)

    /** Starts another app's screen (or a system one) in a task of its own; says so when the
     *  phone has nothing for it, rather than failing. */
    protected fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: android.content.ActivityNotFoundException) {
            say("that isn't on this phone")
        } catch (e: SecurityException) {
            say("Android didn't allow it")
        }
    }
    protected fun open(name: String, arg: String? = null) = host.open(name, arg)
    protected fun refresh() = host.refresh()
}

/** "on"/"off", the way every toggle row shows its value. */
fun onOff(on: Boolean) = if (on) "on" else "off"

/** The next row (from [from], in [direction]) whose key differs from the current row's: the first
 *  letter of a name, a time zone's offset. Wraps around; null when every row has the same key. A
 *  null key (a header, a row before the list proper) is never landed on. */
fun <K> jumpByKey(keys: List<K?>, from: Int, direction: Int): Int? {
    if (keys.isEmpty()) return null
    val start = keys[from.coerceIn(0, keys.lastIndex)]
    var i = from
    repeat(keys.size) {
        i = (i + direction + keys.size) % keys.size
        val key = keys[i]
        if (key != null && key != start) {
            if (direction > 0) return i
            // Going back lands on the first row of the previous group, not its last.
            var first = i
            while (first > 0 && keys[first - 1] == key) first--
            return first
        }
    }
    return null
}
