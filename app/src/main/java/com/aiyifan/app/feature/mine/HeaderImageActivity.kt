package com.aiyifan.app.feature.mine

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
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
    private var imageName: String? = null
    private val saveModel by lazy { ViewModelProvider(this)[HeaderImageSaveViewModel::class.java] }
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) imageName?.let { saveModel.save(this, it) }
        else Toast.makeText(this, R.string.mine_image_save_permission, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maskEnabled = savedInstanceState?.getBoolean("mask_enabled", true) ?: true
        val name = intent.getStringExtra(EXTRA_IMAGE)
        if (name == null || !name.matches(Regex("mine_header_[0-9]{3}\\.webp"))) {
            finish()
            return
        }
        imageName = name
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
        val download = ImageButton(this).apply {
            tag = "header_image_download"
            setImageResource(R.drawable.ic_download)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            contentDescription = getString(R.string.mine_save_image)
            setOnClickListener {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(this@HeaderImageActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else saveModel.save(this@HeaderImageActivity, name)
            }
        }
        controls.addView(download, FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.TOP or Gravity.END).apply {
            marginEnd = buttonSize + resources.getDimensionPixelSize(R.dimen.dp_8)
        })
        root.addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        controls.applySystemBarsPadding(top = true, left = true, right = true)
        setContentView(root)
        saveModel.state.observe(this) { state ->
            download.isEnabled = state != HeaderImageSaveViewModel.State.SAVING
            download.alpha = if (download.isEnabled) 1f else 0.5f
            download.contentDescription = getString(if (download.isEnabled) R.string.mine_save_image else R.string.mine_image_saving)
            val message = when (state) {
                HeaderImageSaveViewModel.State.SAVED -> R.string.mine_image_saved
                HeaderImageSaveViewModel.State.FAILED -> R.string.mine_image_save_failed
                else -> null
            }
            if (message != null) {
                Toast.makeText(this@HeaderImageActivity, message, Toast.LENGTH_LONG).show()
                saveModel.consumeResult()
            }
        }
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
