package com.aiyifan.app.feature.mine

/** Current index changes only when a prepared slide has finished moving into view. */
internal class HeaderSlideshow(val count: Int, initialIndex: Int = 0, initialPaused: Boolean = false) {
    var index = initialIndex.takeIf { it in 0 until count } ?: 0
        private set
    var paused = initialPaused
        private set
    var pendingIndex: Int? = null
        private set
    var transitioning = false
        private set
    private val failures = mutableSetOf<Int>()
    val exhausted get() = count == 0 || failures.size == count
    val nextCandidate get() = nextAfter(index, excludeCurrent = true)
    private var active = false
    private var interacting = false
    private var pendingDirection = 1
    private var ready = false
    private var deadline = Long.MAX_VALUE

    fun activate(now: Long) {
        active = true
        resetDeadline(now)
    }

    fun deactivate() {
        active = false
        interacting = false
        cancelPending()
        deadline = Long.MAX_VALUE
    }

    fun togglePause(now: Long) {
        completeTransition(now)
        paused = !paused
        resetDeadline(now)
    }

    fun loaded(now: Long) {
        if (pendingIndex != null) {
            transitioning = true
        } else {
            ready = true
            resetDeadline(now)
        }
    }

    fun tick(now: Long): Boolean {
        if (!active || interacting || paused || !ready || pendingIndex != null || exhausted || now < deadline) return false
        pendingDirection = 1
        pendingIndex = nextAfter(index, excludeCurrent = true)
        deadline = Long.MAX_VALUE
        return pendingIndex != null
    }

    fun beginInteraction() {
        interacting = true
        cancelPending()
        deadline = Long.MAX_VALUE
    }

    fun endInteraction(now: Long) {
        interacting = false
        resetDeadline(now)
    }

    fun candidate(direction: Int): Int? = nextAfter(index, excludeCurrent = true, direction = direction)

    fun requestManual(direction: Int): Boolean {
        require(direction == -1 || direction == 1)
        if (!active || !ready || exhausted || transitioning) return false
        pendingDirection = direction
        pendingIndex = candidate(direction)
        deadline = Long.MAX_VALUE
        return pendingIndex != null
    }

    fun completeTransition(now: Long) {
        if (!transitioning) return
        index = pendingIndex ?: return
        cancelPending()
        ready = true
        resetDeadline(now)
    }

    fun cancelPending() {
        pendingIndex = null
        transitioning = false
    }

    fun failed(): Boolean {
        if (exhausted) return false
        val failedIndex = pendingIndex ?: index
        failures.add(failedIndex)
        if (pendingIndex != null) {
            pendingIndex = nextAfter(failedIndex, excludeCurrent = true, direction = pendingDirection)
            return pendingIndex != null
        }
        ready = false
        val next = nextAfter(failedIndex, excludeCurrent = false)
        if (next == null) {
            deadline = Long.MAX_VALUE
            return false
        }
        index = next
        return true
    }

    fun rejectNext(candidate: Int, direction: Int = 1): Int? {
        failures.add(candidate)
        val replacement = candidate(direction)
        if (pendingIndex == candidate) pendingIndex = replacement
        return replacement
    }

    private fun nextAfter(start: Int, excludeCurrent: Boolean, direction: Int = 1): Int? =
        (1..count).asSequence().map { (start + direction * it + count) % count }
            .firstOrNull { it !in failures && (!excludeCurrent || it != index) }

    private fun resetDeadline(now: Long) {
        deadline = if (active && !interacting && ready && !paused && !exhausted && pendingIndex == null) {
            now + INTERVAL_MS
        } else Long.MAX_VALUE
    }

    companion object {
        const val INTERVAL_MS = 5_000L
        const val TRANSITION_MS = 450L
    }
}
