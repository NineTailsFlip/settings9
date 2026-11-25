package io.github.ninetailsflip.settings

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import life.furrer.nine.core.ui.Dimens
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.dp

/** Height of a [TwoLineRowView] in dp, for a RowBinder; a header row is [HEADER_ROW_DP]. */
const val TWO_LINE_ROW_DP = 48
const val HEADER_ROW_DP = 30

/** A list row the way Nine apps draw them: a title over a dim line of details. The selected row
 *  is white, the rest sit back in gray; a marked row carries a tick and is green while it isn't
 *  selected. A header is a small gray caps label over the rows it names. Built once and re-bound
 *  in place, as RowListView re-binds visible rows on every key. */
class TwoLineRowView(context: Context) : LinearLayout(context) {
    private val title = TextView(context).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val meta = TextView(context).apply {
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(Palette.GRAY_MUTED)
    }
    private var bound: List<Any?>? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        val side = context.dp(Dimens.SIDE_PADDING_DP)
        setPadding(side, 0, side, 0)
        addView(title)
        addView(meta)
    }

    fun bind(row: Row, selected: Boolean) {
        val key = listOf(row.title, row.meta, selected, row.marked, row.header)
        if (key == bound) return
        bound = key
        if (row.header) {
            gravity = Gravity.BOTTOM
            title.text = row.title.uppercase()
            title.textSize = Dimens.SECTION_SP
            title.letterSpacing = Dimens.SECTION_LETTER_SPACING
            title.setTextColor(Palette.GRAY_MUTED)
            meta.visibility = GONE
            return
        }
        gravity = Gravity.CENTER_VERTICAL
        title.textSize = 16f
        title.letterSpacing = 0f
        title.text = if (row.marked) "✓ ${row.title}" else row.title
        title.setTextColor(
            when {
                // The selection always shows; a marked row keeps its tick when it's selected.
                selected -> Palette.WHITE
                row.marked -> Palette.GREEN
                else -> Palette.GRAY_DIM
            },
        )
        meta.text = row.meta
        meta.visibility = if (row.meta.isEmpty()) GONE else VISIBLE
    }
}
