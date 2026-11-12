package com.aiyifan.app.feature.mine

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import com.aiyifan.app.R
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition

class HeaderImageActivity : AppCompatActivity() {
    private lateinit var image: ZoomImageView
    private var target: CustomViewTarget<ZoomImageView, Drawable>? = null
    private var requests: RequestManager? = null
    private var maskEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maskEnabled = savedInstanceState?.getBoolean("mask_enabled", true) ?: true
        val name = intent.getStringExtra(EXTRA_IMAGE)
        if (name == null || !name.matches(Regex("mine_header_[0-9]{3}\\.webp"))) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        requests = Glide.with(this)
        image = ZoomImageView(this).apply {
            tag = "header_zoom_image"
            contentDescription = getString(R.string.mine_view_image)
        }
        root.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val mask = View(this).apply {
            tag = "header_image_mask"
            setBackgroundColor(0xCC000000.toInt())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = if (maskEnabled) View.VISIBLE else View.GONE
        }
        root.addView(mask, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        image.setOnClickListener {
            maskEnabled = !maskEnabled
            mask.visibility = if (maskEnabled) View.VISIBLE else View.GONE
        }
        val controls = FrameLayout(this)
        val buttonSize = resources.getDimensionPixelSize(R.dimen.dp_48)
        val close = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            contentDescription = getString(R.string.mine_close_image)
            setOnClickListener { finish() }
        }
        controls.addView(close, FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.TOP or Gravity.END))
        root.addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        controls.applySystemBarsPadding(top = true, left = true, right = true)
        setContentView(root)
        image.doOnLayout {
            val manager = requests ?: return@doOnLayout
            val requestTarget = object : CustomViewTarget<ZoomImageView, Drawable>(image) {
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Toast.makeText(this@HeaderImageActivity, R.string.mine_image_unavailable, Toast.LENGTH_SHORT).show()
                }
                override fun onResourceCleared(placeholder: Drawable?) { view.setImageDrawable(null) }
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    view.setImageDrawable(resource)
                }
            }
            target = requestTarget
            manager.load("file:///android_asset/mine_header/$name")
                .override(image.width, image.height).fitCenter().dontAnimate()
                .skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(requestTarget)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("mask_enabled", maskEnabled)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        target?.let { requests?.clear(it) }
        target = null
        requests = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_IMAGE = "header_image"
    }
}
