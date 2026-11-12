package com.aiyifan.app.feature.mine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.aiyifan.app.R

/** A slide and its soft lower edge move together, without allocating per animation frame. */
class HeaderSlideView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bounds = RectF()
    private val textureMatrix = Matrix()
    private val maskMatrix = Matrix()
    private var image: Bitmap? = null
    private var blurred: Bitmap? = null
    private val fadeHeight = resources.getDimensionPixelSize(R.dimen.dp_80).toFloat()
    private val mask = LinearGradient(0f, 0f, 0f, fadeHeight,
        Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP)
    private var texture: BitmapShader? = null
    val hasImage get() = image != null
    val imageAllocationBytes get() = image?.allocationByteCount ?: 0

    fun setImage(bitmap: Bitmap?) {
        image = bitmap
        blurred = null
        texture = null
        blurPaint.shader = null
        updateCrop()
        invalidate()
    }

    fun setBlurred(bitmap: Bitmap) {
        blurred = bitmap
        texture = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        blurPaint.shader = ComposeShader(texture!!, mask, PorterDuff.Mode.DST_IN)
        updateCrop()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateCrop()
    }

    private fun updateCrop() {
        val source = image ?: return
        if (width == 0 || height == 0) return
        // Decode once at expanded size and center-crop at every height, never stretch.
        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val drawWidth = source.width * scale
        val drawHeight = source.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        bounds.set(left, top, left + drawWidth, top + drawHeight)
        blurred?.let {
            textureMatrix.setScale(drawWidth / it.width, drawHeight / it.height)
            textureMatrix.postTranslate(left, top)
            texture?.setLocalMatrix(textureMatrix)
            maskMatrix.setTranslate(0f, height - fadeHeight)
            mask.setLocalMatrix(maskMatrix)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        image?.let { canvas.drawBitmap(it, null, bounds, imagePaint) }
        if (blurred != null) canvas.drawRect(0f, height - fadeHeight, width.toFloat(), height.toFloat(), blurPaint)
    }
}
