package com.aiyifan.app.core.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.aiyifan.app.R
import kotlin.math.roundToInt

class AspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val ratioWidth: Int
    private val ratioHeight: Int

    init {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.AspectRatioFrameLayout)
        ratioWidth = attributes.getInt(R.styleable.AspectRatioFrameLayout_ratioWidth, 16).coerceAtLeast(1)
        ratioHeight = attributes.getInt(R.styleable.AspectRatioFrameLayout_ratioHeight, 9).coerceAtLeast(1)
        attributes.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width.toFloat() * ratioHeight / ratioWidth).roundToInt()
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, height)
    }
}
