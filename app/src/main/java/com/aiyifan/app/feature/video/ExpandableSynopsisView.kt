package com.aiyifan.app.feature.video

import android.content.Context
import android.text.StaticLayout
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aiyifan.app.R

/** Keeps the synopsis compact without including the separate director/cast credits. */
class ExpandableSynopsisView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val toggleSize = resources.getDimensionPixelSize(R.dimen.dp_44)
    private val synopsisText = TextView(context).apply {
        tag = "synopsis_text"
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.sp_14))
        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
    }
    private val toggle = ImageButton(context).apply {
        tag = "synopsis_toggle"
        setImageResource(R.drawable.ic_chevron_right)
        val inset = resources.getDimensionPixelSize(R.dimen.dp_12)
        setPadding(inset, inset, inset, inset)
        val background = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, background, true)
        if (background.resourceId != 0) setBackgroundResource(background.resourceId)
        visibility = GONE
        setOnClickListener { toggleExpanded() }
    }
    private var expanded = false
    private var overflows = false

    init {
        addView(synopsisText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(toggle, LayoutParams(toggleSize, toggleSize, Gravity.BOTTOM or Gravity.END))
        setOnClickListener { toggleExpanded() }
        isClickable = false
    }

    fun setSynopsis(value: String) {
        val normalized = value.trim()
        if (synopsisText.text.toString() != normalized) expanded = false
        synopsisText.text = normalized
        visibility = if (normalized.isEmpty()) GONE else VISIBLE
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
            .coerceAtLeast(1)
        // Measure at the full width first: short text must not gain an unnecessary arrow.
        val fullLayout = StaticLayout.Builder.obtain(
            synopsisText.text, 0, synopsisText.text.length, synopsisText.paint, availableWidth,
        ).setIncludePad(synopsisText.includeFontPadding)
            .setBreakStrategy(synopsisText.breakStrategy)
            .setHyphenationFrequency(synopsisText.hyphenationFrequency)
            .build()
        overflows = fullLayout.lineCount > 2
        if (!overflows) expanded = false
        synopsisText.maxLines = if (expanded) Int.MAX_VALUE else 2
        synopsisText.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
        synopsisText.setPaddingRelative(0, 0, if (overflows) toggleSize else 0, 0)
        synopsisText.minHeight = if (overflows) toggleSize else 0
        toggle.visibility = if (overflows) VISIBLE else GONE
        toggle.rotation = if (expanded) 270f else 90f
        toggle.contentDescription = context.getString(
            if (expanded) R.string.video_synopsis_collapse else R.string.video_synopsis_expand,
        )
        isClickable = overflows
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun toggleExpanded() {
        if (!overflows) return
        expanded = !expanded
        requestLayout()
    }
}
