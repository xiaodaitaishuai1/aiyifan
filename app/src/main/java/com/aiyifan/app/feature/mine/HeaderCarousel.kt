package com.aiyifan.app.feature.mine

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Owns exactly two Glide targets and at most two small blur thumbnails. */
internal class HeaderCarousel(
    private val requests: RequestManager,
    private val images: List<String>,
    private val state: HeaderSlideshow,
    first: HeaderSlideView,
    second: HeaderSlideView,
    private val decodeHeight: () -> Int,
    private val onChanged: () -> Unit,
) {
    private class Slot(val view: HeaderSlideView) {
        var target: CustomTarget<Bitmap>? = null
        var blurTask: Future<*>? = null
        var generation = 0
    }

    private val slots = arrayOf(Slot(first), Slot(second))
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var current = 0
    private var running = false
    private var ready = false
    private var animation: ValueAnimator? = null
    private var loadingIndex: Int? = null
    private var preparedIndex: Int? = null
    private var awaitingNext = false
    private var touching = false
    private var direction = 1
    private var dragOffset = 0f
    val canExpand get() = running && ready
    private val advance = Runnable {
        if (state.tick(SystemClock.uptimeMillis())) {
            direction = 1
            awaitingNext = true
            beginPreparedTransition()
        }
    }

    fun start() {
        if (running || state.exhausted) return
        running = true
        state.activate(SystemClock.uptimeMillis())
        slots[current].view.visibility = View.VISIBLE
        load(slots[current], state.index, next = false)
    }

    fun togglePause() {
        settleAnimation()
        cancelPendingLoad()
        state.togglePause(SystemClock.uptimeMillis())
        onChanged()
        schedule()
    }

    fun beginTouch() {
        settleAnimation()
        touching = true
        dragOffset = 0f
        direction = 1
        awaitingNext = false
        state.beginInteraction()
        handler.removeCallbacks(advance)
    }

    fun drag(offset: Float) {
        if (!touching || !running || !ready) return
        val desiredDirection = if (offset > 0) -1 else 1
        if (direction != desiredDirection) {
            cancelPendingLoad()
            slots[current].view.translationX = 0f
            direction = desiredDirection
        }
        dragOffset = offset.coerceIn(-slots[current].view.width.toFloat(), slots[current].view.width.toFloat())
        ensureCandidate(direction)
        drawDrag()
    }

    fun finishTouch(switchImage: Boolean) {
        if (!touching) return
        touching = false
        if (!running || !ready) {
            state.endInteraction(SystemClock.uptimeMillis())
            schedule()
            return
        }
        if (switchImage && dragOffset != 0f && state.requestManual(direction)) {
            awaitingNext = true
            ensureCandidate(direction)
            beginPreparedTransition()
        } else {
            animateSlides(commit = false)
        }
    }

    private fun ensureCandidate(requestedDirection: Int) {
        val candidate = state.candidate(requestedDirection) ?: return
        if (candidate != preparedIndex && candidate != loadingIndex) {
            preparedIndex = null
            load(slots[1 - current], candidate, next = true, requestedDirection)
        }
    }

    private fun drawDrag() {
        if (!touching || preparedIndex == null || preparedIndex != state.candidate(direction)) return
        val outgoing = slots[current].view
        val incoming = slots[1 - current].view
        outgoing.translationX = dragOffset
        incoming.translationX = dragOffset + outgoing.width * direction
        incoming.visibility = View.VISIBLE
    }

    fun prepareViewer(): Int? {
        if (!canExpand) return null
        settleAnimation()
        val index = state.index
        stop()
        return index
    }

    private fun settleAnimation() {
        animation?.end()
    }

    private fun cancelPendingLoad() {
        clear(slots[1 - current])
        loadingIndex = null
        preparedIndex = null
        awaitingNext = false
        state.cancelPending()
    }

    private fun load(slot: Slot, index: Int, next: Boolean, requestedDirection: Int = 1) {
        clear(slot)
        if (next) loadingIndex = index
        val version = slot.generation
        val view = slot.view
        val targetWidth = view.width
        val targetHeight = decodeHeight()
        view.visibility = if (next) View.INVISIBLE else View.VISIBLE
        val target = object : CustomTarget<Bitmap>(targetWidth, targetHeight) {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                if (!running || slot.generation != version) return
                view.setImage(resource)
                // Copy only a tiny thumbnail while Glide owns the source. Workers never retain
                // a pooled full-size bitmap that could be cleared/reused during navigation.
                val thumb = Bitmap.createBitmap((resource.width / 8).coerceAtLeast(1),
                    (resource.height / 8).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                Canvas(thumb).drawBitmap(resource, null, Rect(0, 0, thumb.width, thumb.height),
                    Paint(Paint.FILTER_BITMAP_FLAG))
                slot.blurTask = worker.submit {
                    val pixels = IntArray(thumb.width * thumb.height)
                    thumb.getPixels(pixels, 0, thumb.width, 0, 0, thumb.width, thumb.height)
                    val blurred = HeaderBlur.blur(pixels, thumb.width, thumb.height)
                    if (!Thread.currentThread().isInterrupted) {
                        thumb.setPixels(blurred, 0, thumb.width, 0, 0, thumb.width, thumb.height)
                        handler.post {
                            if (running && slot.generation == version) {
                                view.setBlurred(thumb)
                                if (next) {
                                    loadingIndex = null
                                    preparedIndex = index
                                    if (touching) drawDrag() else beginPreparedTransition()
                                } else {
                                    state.loaded(SystemClock.uptimeMillis())
                                    ready = true
                                    onChanged()
                                    schedule()
                                }
                            }
                        }
                    }
                }
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                handler.post {
                    if (running && slot.generation == version) {
                        if (next) {
                            loadingIndex = null
                            val replacement = state.rejectNext(index, requestedDirection)
                            if (replacement != null) load(slot, replacement, next = true, requestedDirection) else {
                                clear(slot)
                                awaitingNext = false
                                if (!touching) {
                                    state.endInteraction(SystemClock.uptimeMillis())
                                    schedule()
                                }
                                onChanged()
                            }
                        } else if (state.failed()) {
                            load(slot, state.pendingIndex ?: state.index, next)
                        } else {
                            clear(slot)
                            onChanged()
                        }
                    }
                }
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                view.setImage(null)
            }
        }
        slot.target = target
        requests.asBitmap().load("file:///android_asset/mine_header/${images[index]}")
            .override(targetWidth, targetHeight).centerCrop().disallowHardwareConfig().dontAnimate()
            .skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE).into(target)
    }

    private fun beginPreparedTransition() {
        if (!running || touching || !awaitingNext || preparedIndex == null || preparedIndex != state.pendingIndex) return
        awaitingNext = false
        state.loaded(SystemClock.uptimeMillis())
        animateSlides(commit = true)
    }

    private fun animateSlides(commit: Boolean) {
        val outgoing = slots[current]
        val incoming = slots[1 - current]
        val width = outgoing.view.width.toFloat()
        val start = outgoing.view.translationX
        if (commit) incoming.view.visibility = View.VISIBLE
        val end = if (commit) -width * direction else 0f
        if (!commit && start == 0f) {
            completeAnimation(outgoing, incoming, commit = false)
            return
        }
        incoming.view.translationX = start + width * direction
        val animator = ValueAnimator.ofFloat(start, end).apply {
            duration = HeaderSlideshow.TRANSITION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val offset = it.animatedValue as Float
                outgoing.view.translationX = offset
                incoming.view.translationX = offset + width * direction
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                    animation = null
                    completeAnimation(outgoing, incoming, commit)
                }
            })
        }
        // Assign before start: system animation scale 0 can finish synchronously.
        animation = animator
        animator.start()
    }

    private fun completeAnimation(outgoing: Slot, incoming: Slot, commit: Boolean) {
        if (commit) {
            current = 1 - current
            preparedIndex = null
            incoming.view.translationX = 0f
            clear(outgoing)
            state.completeTransition(SystemClock.uptimeMillis())
        } else {
            outgoing.view.translationX = 0f
            cancelPendingLoad()
        }
        dragOffset = 0f
        state.endInteraction(SystemClock.uptimeMillis())
        onChanged()
        schedule()
    }

    private fun schedule() {
        handler.removeCallbacks(advance)
        if (running && !touching && ready && !state.paused && !state.exhausted && state.pendingIndex == null) {
            handler.postDelayed(advance, HeaderSlideshow.INTERVAL_MS)
            if (loadingIndex == null && preparedIndex == null) {
                state.nextCandidate?.let { load(slots[1 - current], it, next = true) }
            }
        }
    }

    private fun clear(slot: Slot) {
        slot.generation++
        slot.blurTask?.cancel(true)
        slot.blurTask = null
        slot.view.setImage(null)
        slot.target?.let(requests::clear)
        slot.target = null
        slot.view.translationX = 0f
        slot.view.visibility = View.INVISIBLE
    }

    fun stop() {
        running = false
        touching = false
        dragOffset = 0f
        ready = false
        loadingIndex = null
        preparedIndex = null
        awaitingNext = false
        animation?.removeAllListeners()
        animation?.cancel()
        animation = null
        state.deactivate()
        handler.removeCallbacksAndMessages(null)
        slots.forEach(::clear)
        onChanged()
    }

    fun dispose() {
        stop()
        worker.shutdownNow()
    }
}
