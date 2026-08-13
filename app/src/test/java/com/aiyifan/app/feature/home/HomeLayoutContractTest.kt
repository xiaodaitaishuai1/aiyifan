package com.aiyifan.app.feature.home

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class HomeLayoutContractTest {

    @Test
    fun `home banner reserves a taller sixteen by eight carousel frame with pager controls`() {
        val root = root(layout("item_home_banner"))

        assertEquals("16", view(root, "bannerFrame").getAttribute("app:ratioWidth"))
        assertEquals("8", view(root, "bannerFrame").getAttribute("app:ratioHeight"))
        assertEquals("@+id/bannerPager", view(root, "bannerPager").getAttribute("android:id"))
        assertEquals(
            "@+id/bannerPageIndicator",
            view(root, "bannerPageIndicator").getAttribute("android:id"),
        )
        assertEquals("LinearLayout", view(root, "bannerPageIndicator").tagName)
    }

    @Test
    fun `home banner carousel page contains a poster and title`() {
        val root = root(layout("item_home_banner_page"))

        assertEquals("@+id/bannerPagePoster", view(root, "bannerPagePoster").getAttribute("android:id"))
        assertEquals("@+id/bannerPageTitle", view(root, "bannerPageTitle").getAttribute("android:id"))
        assertTrue(layout("item_home_banner_page").readText().contains("@drawable/bg_home_banner_scrim"))
        assertTrue(drawable("bg_home_banner_scrim").readText().contains("android:startColor=\"#E6000000\""))
    }

    @Test
    fun `home adapter owns a cancellable pager carousel`() {
        val adapter = source("feature/home/HomeVideoAdapter.kt").readText()

        assertTrue(adapter.contains("ViewPager2"))
        assertTrue(adapter.contains("bindBannerPoster(binding.bannerPagePoster, video.coverUrl)"))
        assertTrue(adapter.contains("HomeBannerCarouselPolicy.nextPage"))
        assertTrue(adapter.contains("removeCallbacks"))
        assertTrue(adapter.contains("bannerPageIndicator"))
        assertTrue(adapter.contains("renderIndicatorDots"))
        assertTrue(adapter.contains("HomePosterSizePolicy.pxFromDp(INDICATOR_SELECTED_WIDTH_DP, density)"))
        assertTrue(adapter.contains("bindBannerPoster"))
        assertTrue(adapter.contains("bindCardPoster"))
        assertTrue(adapter.contains("private var isAttached = false"))
        assertTrue(adapter.contains("fun setAttached"))
        assertTrue(adapter.contains("isAttached && isFragmentVisible"))
        assertTrue(adapter.contains("onViewDetachedFromWindow"))
        assertTrue(adapter.contains("onViewAttachedToWindow"))
        val detachedHandler = adapter
            .substringAfter("override fun onViewDetachedFromWindow")
            .substringBefore("override fun onViewAttachedToWindow")
        assertTrue(detachedHandler.contains("currentBannerHolder === holder"))
        assertTrue(detachedHandler.contains("currentBannerHolder = null"))
        val attachedHandler = adapter
            .substringAfter("override fun onViewAttachedToWindow")
            .substringBefore("override fun getItemCount")
        assertTrue(attachedHandler.contains("currentBannerHolder = holder"))
        assertTrue(attachedHandler.contains("holder.setAttached(true, isBannerVisible)"))
        assertTrue(
            attachedHandler.indexOf("currentBannerHolder = holder") <
                attachedHandler.indexOf("holder.setAttached(true, isBannerVisible)"),
        )
        assertTrue(detachedHandler.contains("holder.setAttached(false, isBannerVisible)"))
    }

    @Test
    fun `home card reserves a two by three portrait frame and limits title lines`() {
        val root = root(layout("item_home_video"))
        val adapter = source("feature/home/HomeVideoAdapter.kt").readText()
        val titleRow = view(root, "cardTitle").parentNode as Element

        assertEquals("2", view(root, "cardFrame").getAttribute("app:ratioWidth"))
        assertEquals("3", view(root, "cardFrame").getAttribute("app:ratioHeight"))
        assertEquals("@dimen/dp_6", root.getAttribute("android:layout_marginStart"))
        assertEquals("@dimen/dp_6", root.getAttribute("android:layout_marginEnd"))
        assertTrue(adapter.contains("bindCardPoster(binding.cardPoster, video.coverUrl"))
        assertEquals("LinearLayout", titleRow.tagName)
        assertEquals("bottom", titleRow.getAttribute("android:layout_gravity"))
        assertEquals("@dimen/dp_44", view(root, "cardBottomScrim").getAttribute("android:layout_height"))
        assertEquals("bottom", view(root, "cardBottomScrim").getAttribute("android:layout_gravity"))
        assertEquals("@dimen/dp_0", view(root, "cardTitle").getAttribute("android:layout_width"))
        assertEquals("1", view(root, "cardTitle").getAttribute("android:maxLines"))
        assertEquals("@color/text_primary", view(root, "cardTitle").getAttribute("android:textColor"))
        assertEquals("@dimen/sp_12", view(root, "cardTitle").getAttribute("android:textSize"))
        assertEquals("@color/text_secondary", view(root, "cardMeta").getAttribute("android:textColor"))
        assertEquals("@dimen/sp_11", view(root, "cardMeta").getAttribute("android:textSize"))
    }

    @Test
    fun `home fragment configures a two column grid and leaves hot list untouched`() {
        val home = source("feature/home/HomeFragment.kt").readText()

        assertTrue(home.contains("GridLayoutManager(requireContext(), 2)"))
        assertTrue(home.contains("HomeVideoAdapter"))
        assertFalse(home.contains("LinearLayoutManager(requireContext())"))
        assertTrue(source("feature/hot/HotFragment.kt").readText().contains("LinearLayoutManager(requireContext())"))
    }

    @Test
    fun `home loading footer is theme-aware and spans a grid row`() {
        val footer = sequenceOf(
            File("src/main/res/layout/item_home_loading.xml"),
            File("app/src/main/res/layout/item_home_loading.xml"),
        ).firstOrNull(File::isFile)
        val adapter = source("feature/home/HomeVideoAdapter.kt").readText()
        val fragment = source("feature/home/HomeFragment.kt").readText()

        assertTrue(footer != null)
        assertTrue(adapter.contains("HomeFeedItem.Loading"))
        assertTrue(adapter.contains("R.drawable.bg_poster"))
        assertTrue(adapter.contains("DiskCacheStrategy.ALL"))
        assertTrue(fragment.contains("adapter.setLoadMoreLoading(false)"))
    }

    @Test
    fun `home contains a hidden VPN quick connect button`() {
        val home = root(layout("fragment_home"))
        val button = view(home, "vpnQuickConnectButton")

        assertEquals("gone", button.getAttribute("android:visibility"))
        assertEquals("@string/home_vpn_connect", button.getAttribute("android:text"))
    }

    private fun layout(name: String): File = sequenceOf(
        File("src/main/res/layout/$name.xml"),
        File("app/src/main/res/layout/$name.xml"),
    ).first(File::isFile)

    private fun drawable(name: String): File = sequenceOf(
        File("src/main/res/drawable/$name.xml"),
        File("app/src/main/res/drawable/$name.xml"),
    ).first(File::isFile)

    private fun root(file: File): Element = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(file)
        .documentElement

    private fun source(path: String): File = sequenceOf(
        File("src/main/java/com/aiyifan/app/$path"),
        File("app/src/main/java/com/aiyifan/app/$path"),
    ).first(File::isFile)

    private fun view(root: Element, id: String): Element =
        (sequenceOf(root) + (0 until root.getElementsByTagName("*").length).asSequence()
            .map(root.getElementsByTagName("*")::item)
            .map { it as Element })
            .first { it.getAttribute("android:id").substringAfter("@+id/") == id }
}
