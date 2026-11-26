package io.github.ninetailsflip.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import life.furrer.nine.core.settings.openNineSettings
import life.furrer.nine.core.ui.Dimens
import life.furrer.nine.core.ui.HintBar
import life.furrer.nine.core.ui.MenuItem
import life.furrer.nine.core.ui.MenuSheet
import life.furrer.nine.core.ui.NineActivity
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.RowBinder
import life.furrer.nine.core.ui.RowListView
import life.furrer.nine.core.ui.dp
import life.furrer.nine.core.ui.relaunch

/**
 * Every page of Settings, one activity each: the page's title, what it's for, its rows, and -
 * when a page asks for a password, a PIN or a name - a field under them. Which page comes from
 * [EXTRA_SCREEN] (and [EXTRA_ARG]), or, for an `android.settings.*` intent from another app or
 * the system, from [Screens.forIntent].
 */
open class ScreenActivity : NineActivity(), ScreenHost {

    protected lateinit var screen: Screen
    private var rows: List<Row> = emptyList()
    private var field: FieldRequest? = null
    private var message: String? = null
    private val results = mutableMapOf<Int, (Int, Intent?) -> Unit>()

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var rowListView: RowListView
    private lateinit var input: EditText
    private lateinit var hintBar: HintBar
    private lateinit var menuSheet: MenuSheet

    override val context: Context get() = this

    private val binder = object : RowBinder {
        override fun rowCount() = rows.size
        override fun createRowView(context: Context): View = TwoLineRowView(context)
        override fun bind(view: View, index: Int, selected: Boolean) = (view as TwoLineRowView).bind(rows[index], selected)
        override fun rowHeight(index: Int) = if (rows[index].header) HEADER_ROW_DP else TWO_LINE_ROW_DP
        override fun isSelectable(index: Int) = !rows[index].header
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StockSettings.hideLauncherEntry(this)
        val (name, arg) = pageFor(intent)
        screen = Screens.create(name, arg).also { it.host = this }

        title = TextView(this).apply {
            textSize = 20f
            setTextColor(Palette.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(12), dp(Dimens.SIDE_PADDING_DP), 0)
        }
        subtitle = TextView(this).apply {
            textSize = 12f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Palette.GRAY_MUTED)
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(2), dp(Dimens.SIDE_PADDING_DP), dp(8))
        }
        rowListView = RowListView(this).apply { setBinder(binder) }
        input = EditText(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setHintTextColor(Palette.GRAY_FAINT)
            setBackgroundColor(Palette.BLACK)
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(8), dp(Dimens.SIDE_PADDING_DP), dp(8))
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            visibility = View.GONE
            // Not focusable until a page asks: a focusable field in a fresh window takes the
            // keyboard's connection at once, and the keypad's * and # with it.
            isFocusable = false
            setOnEditorActionListener { _, _, _ -> submit(); true }
        }
        hintBar = HintBar(this)
        menuSheet = MenuSheet(this)
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Palette.BLACK)
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                addView(
                    LinearLayout(this@ScreenActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(title)
                        addView(subtitle)
                        addView(rowListView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                        addView(input)
                        addView(hintBar)
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                addView(menuSheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            },
        )
        rows = rowsOf()
        rowListView.notifyDataChanged()
        val start = intent.getIntExtra(EXTRA_ROW, -1).takeIf { it >= 0 } ?: runCatching { screen.initialRow() }.getOrDefault(0)
        rowListView.setSelectedIndex(start.coerceIn(0, rows.lastIndex.coerceAtLeast(0)))
        render()
    }

    /** The page an intent opens: [EXTRA_SCREEN], or an `android.settings.*` action's. */
    protected open fun pageFor(intent: Intent): Pair<String, String?> =
        intent.getStringExtra(EXTRA_SCREEN)?.let { it to intent.getStringExtra(EXTRA_ARG) } ?: Screens.forIntent(intent)

    override fun onResume() {
        super.onResume()
        safely("show") { screen.onShow() }
        refresh()
    }

    override fun onPause() {
        safely("hide") { screen.onHide() }
        super.onPause()
    }

    // ── ScreenHost ──────────────────────────────────────────────────────────────────────────

    override val selected: Int get() = rowListView.selectedIndex

    override fun select(index: Int) {
        rowListView.setSelectedIndex(index.coerceIn(0, rows.lastIndex.coerceAtLeast(0)))
    }

    override fun refresh() {
        rows = rowsOf()
        rowListView.notifyDataChanged()
        render()
    }

    /** A page that breaks still shows a way back rather than a crash. */
    private fun rowsOf(): List<Row> = try {
        screen.rows()
    } catch (e: Exception) {
        Log.w(TAG, "${screen.title}: couldn't list its rows", e)
        listOf(Row("This page didn't open", "‹ goes back") { finish() })
    }

    private fun render() {
        title.text = screen.title
        val sub = runCatching { screen.subtitle() }.getOrDefault("")
        subtitle.text = sub
        subtitle.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
        hintBar.setText(message ?: if (field != null) "type · ok done · ‹ on an empty field: cancel" else screen.hint())
    }

    override fun say(text: String) {
        message = text
        render()
    }

    override fun open(name: String, arg: String?) {
        startActivity(intentFor(this, name, arg))
    }

    override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) {
        val code = REQUEST_BASE + results.size
        results[code] = onResult
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, code)
        } catch (e: Exception) {
            results.remove(code)
            Log.w(TAG, "Couldn't start $intent", e)
            say("that isn't available on this phone")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        results.remove(requestCode)?.let { callback -> safely("take a result") { callback(resultCode, data) } }
        refresh()
    }

    override fun ask(request: FieldRequest) {
        field = request
        input.setText(request.initial)
        input.setSelection(input.text.length)
        input.hint = request.prompt
        input.inputType = when (request.kind) {
            FieldKind.PIN -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            // Shown as typed: on a keypad, seeing each letter is how you know the right one came.
            FieldKind.PASSWORD -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            FieldKind.TEXT -> InputType.TYPE_CLASS_TEXT
        }
        input.visibility = View.VISIBLE
        input.isFocusable = true
        input.isFocusableInTouchMode = true
        input.requestFocus()
        render()
    }

    /** Hands the text to the page. Unless it asks again (a PIN's second time), a fresh screen
     *  takes the keyboard's connection away, so `*` and `#` work again (core's relaunch). */
    private fun submit() {
        val request = field ?: return
        field = null
        safely("take the typed text") { request.onDone(input.text.toString()) }
        if (field == null) restart()
    }

    private fun cancelField() {
        field = null
        message = null
        restart()
    }

    private fun restart() {
        val again = Intent(intent).setComponent(ComponentName(this, javaClass))
            .putExtra(EXTRA_ROW, rowListView.selectedIndex)
            .putExtra(EXTRA_MESSAGE, message)
        relaunch(again)
    }

    override fun close() = finish()

    // ── Keys ────────────────────────────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (field != null && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> if (input.text.isEmpty()) {
                    cancelField()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    submit()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = try {
        handleKeyDown(keyCode, event)
    } catch (e: Exception) {
        Log.w(TAG, "${screen.title}: a key failed", e)
        say("that didn't work")
        true
    }

    private fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (field != null) return super.onKeyDown(keyCode, event)
        if (menuSheet.handleKeyDown(keyCode, event)) return true
        message = null
        val index = rowListView.selectedIndex
        val row = rows.getOrNull(index)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> rowListView.moveSelection(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> rowListView.moveSelection(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                val adjust = row?.adjust
                if (adjust != null) adjust(direction) else screen.jump(index, direction)?.let(rowListView::setSelectedIndex)
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                val ok = row?.ok
                val adjust = row?.adjust
                if (ok != null) ok() else adjust?.invoke(1)
            }
            KeyEvent.KEYCODE_BACK -> if (!screen.back()) finish()
            KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_MENU -> openMenu()
            else -> return super.onKeyDown(keyCode, event)
        }
        if (!isFinishing) refresh()
        return true
    }

    private fun openMenu() {
        val items = screen.menu().map { (label, action) -> MenuItem(label) { safely(label) { action() }; refresh() } }.toMutableList()
        if (screen !is MainScreen) items += MenuItem("All settings") { open(Screens.MAIN) }
        items += MenuItem("Settings' look") { openNineSettings() }
        menuSheet.show(items)
    }

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "${screen.title}: couldn't $what", e)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_MESSAGE)?.let { say(it) }
    }

    companion object {
        private const val TAG = "Settings"
        const val EXTRA_SCREEN = "io.github.ninetailsflip.settings.SCREEN"
        const val EXTRA_ARG = "io.github.ninetailsflip.settings.ARG"
        private const val EXTRA_ROW = "io.github.ninetailsflip.settings.ROW"
        private const val EXTRA_MESSAGE = "io.github.ninetailsflip.settings.MESSAGE"
        private const val REQUEST_BASE = 900

        fun intentFor(context: Context, name: String, arg: String? = null): Intent =
            Intent(context, ScreenActivity::class.java).putExtra(EXTRA_SCREEN, name).putExtra(EXTRA_ARG, arg)
    }
}
