package com.aiyifan.app.feature.home

object HomeBannerCarouselPolicy {

    fun nextPage(currentPage: Int, pageCount: Int): Int =
        if (pageCount <= 0) 0 else (currentPage + 1) % pageCount

    fun canAutoScroll(pageCount: Int, isVisible: Boolean): Boolean = isVisible && pageCount > 1
}
