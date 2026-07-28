# Homepage Banner Grid Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the home category feed as one full-width featured banner followed by two-column video cards, without changing the hot screen.

**Architecture:** A pure `HomeFeedItemFactory` maps a `List<VideoSummary>` to a typed sequence where the first video is the banner and later videos are grid cards. `HomeVideoAdapter` owns the two RecyclerView view types; `HomeFragment` switches only its RecyclerView to a two-column grid and keeps search, categories, refresh, errors, and playback navigation unchanged.

**Tech Stack:** Kotlin, Android Views, RecyclerView `GridLayoutManager`, View Binding, Glide, JUnit 4, XML resources.

---

## File Structure

- Create: `app/src/main/java/com/aiyifan/app/feature/home/HomeFeedItemFactory.kt` - pure feed split and view-type model.
- Create: `app/src/main/java/com/aiyifan/app/feature/home/HomeVideoAdapter.kt` - home-only banner/grid adapter and Glide binding.
- Create: `app/src/main/java/com/aiyifan/app/core/ui/AspectRatioFrameLayout.kt` - measures a frame against a layout-supplied width/height ratio.
- Create: `app/src/main/res/values/attrs.xml` - declares `AspectRatioFrameLayout` dimensions.
- Create: `app/src/main/res/drawable/bg_home_banner_scrim.xml` - bottom title contrast overlay.
- Create: `app/src/main/res/layout/item_home_banner.xml` - full-width 16:7 banner.
- Create: `app/src/main/res/layout/item_home_video.xml` - one 16:9 grid card.
- Modify: `app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt` - use `HomeVideoAdapter` and a two-column grid.
- Create: `app/src/test/java/com/aiyifan/app/feature/home/HomeFeedItemFactoryTest.kt` - feed split unit tests.
- Create: `app/src/test/java/com/aiyifan/app/feature/home/HomeLayoutContractTest.kt` - XML layout and grid configuration contract tests.

### Task 1: Define and Test Home Feed Splitting

**Files:**
- Create: `app/src/test/java/com/aiyifan/app/feature/home/HomeFeedItemFactoryTest.kt`
- Create: `app/src/main/java/com/aiyifan/app/feature/home/HomeFeedItemFactory.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
class HomeFeedItemFactoryTest {
    @Test
    fun `empty videos create no home feed items`() {
        assertTrue(HomeFeedItemFactory.create(emptyList()).isEmpty())
    }

    @Test
    fun `first video becomes banner and remaining videos become cards`() {
        val videos = listOf(video("banner"), video("card-one"), video("card-two"))

        assertEquals(
            listOf(
                HomeFeedItem.Banner(videos[0]),
                HomeFeedItem.Card(videos[1]),
                HomeFeedItem.Card(videos[2]),
            ),
            HomeFeedItemFactory.create(videos),
        )
    }

    private fun video(mediaKey: String) = VideoSummary(
        mediaKey = mediaKey,
        title = mediaKey,
        coverUrl = "",
        videoType = 0,
    )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew.bat test --tests "com.aiyifan.app.feature.home.HomeFeedItemFactoryTest"`

Expected: compilation failure because `HomeFeedItemFactory` and `HomeFeedItem` do not exist.

- [ ] **Step 3: Implement the smallest feed model**

```kotlin
sealed interface HomeFeedItem {
    val video: VideoSummary

    data class Banner(override val video: VideoSummary) : HomeFeedItem
    data class Card(override val video: VideoSummary) : HomeFeedItem
}

object HomeFeedItemFactory {
    fun create(videos: List<VideoSummary>): List<HomeFeedItem> =
        videos.mapIndexed { index, video ->
            if (index == 0) HomeFeedItem.Banner(video) else HomeFeedItem.Card(video)
        }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew.bat test --tests "com.aiyifan.app.feature.home.HomeFeedItemFactoryTest"`

Expected: all two tests pass.

- [ ] **Step 5: Commit the completed data-splitting unit**

```powershell
git add app/src/main/java/com/aiyifan/app/feature/home/HomeFeedItemFactory.kt app/src/test/java/com/aiyifan/app/feature/home/HomeFeedItemFactoryTest.kt
git commit -m "feat(app): 增加首页横幅数据分流"
```

### Task 2: Add Responsive Banner and Grid-Card Resources

**Files:**
- Create: `app/src/test/java/com/aiyifan/app/feature/home/HomeLayoutContractTest.kt`
- Create: `app/src/main/java/com/aiyifan/app/core/ui/AspectRatioFrameLayout.kt`
- Create: `app/src/main/res/values/attrs.xml`
- Create: `app/src/main/res/drawable/bg_home_banner_scrim.xml`
- Create: `app/src/main/res/layout/item_home_banner.xml`
- Create: `app/src/main/res/layout/item_home_video.xml`

- [ ] **Step 1: Write failing layout contracts**

```kotlin
@Test
fun `home banner reserves a sixteen by seven frame and overlays its title`() {
    val root = root(layout("item_home_banner"))
    assertEquals("16", view(root, "bannerFrame").getAttribute("app:ratioWidth"))
    assertEquals("7", view(root, "bannerFrame").getAttribute("app:ratioHeight"))
    assertEquals("@+id/bannerTitle", view(root, "bannerTitle").getAttribute("android:id"))
}

@Test
fun `home card reserves a sixteen by nine frame and limits title lines`() {
    val root = root(layout("item_home_video"))
    assertEquals("16", view(root, "cardFrame").getAttribute("app:ratioWidth"))
    assertEquals("9", view(root, "cardFrame").getAttribute("app:ratioHeight"))
    assertEquals("2", view(root, "cardTitle").getAttribute("android:maxLines"))
}
```

- [ ] **Step 2: Run the layout contract test to verify it fails**

Run: `./gradlew.bat test --tests "com.aiyifan.app.feature.home.HomeLayoutContractTest"`

Expected: failure because the two layout resource files do not exist.

- [ ] **Step 3: Implement ratio frame, overlay, and the two layouts**

```kotlin
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
```

Use `AspectRatioFrameLayout` with `app:ratioWidth="16"` and `app:ratioHeight="7"` for `bannerFrame`; put a `centerCrop` `ImageView`, `bg_home_banner_scrim`, and bottom-aligned `bannerTitle` inside it. Use the same view with `16:9` for `cardFrame`, then add a two-line title and one-line status below it. Both poster image views keep `@drawable/bg_poster` as their placeholder background.

- [ ] **Step 4: Run the layout contract test to verify it passes**

Run: `./gradlew.bat test --tests "com.aiyifan.app.feature.home.HomeLayoutContractTest"`

Expected: both XML contract tests pass.

- [ ] **Step 5: Commit the resource unit**

```powershell
git add app/src/main/java/com/aiyifan/app/core/ui/AspectRatioFrameLayout.kt app/src/main/res/values/attrs.xml app/src/main/res/drawable/bg_home_banner_scrim.xml app/src/main/res/layout/item_home_banner.xml app/src/main/res/layout/item_home_video.xml app/src/test/java/com/aiyifan/app/feature/home/HomeLayoutContractTest.kt
git commit -m "feat(app): 增加首页横幅和网格卡片布局"
```

### Task 3: Bind the Home Feed Without Affecting Hot

**Files:**
- Create: `app/src/main/java/com/aiyifan/app/feature/home/HomeVideoAdapter.kt`
- Modify: `app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt`
- Modify: `app/src/test/java/com/aiyifan/app/feature/home/HomeLayoutContractTest.kt`

- [ ] **Step 1: Extend the failing layout contract for the grid**

```kotlin
@Test
fun `home fragment configures a two column grid and leaves hot list untouched`() {
    val source = source("feature/home/HomeFragment.kt").readText()
    assertTrue(source.contains("GridLayoutManager(requireContext(), 2)"))
    assertTrue(source.contains("HomeVideoAdapter"))
    assertFalse(source.contains("LinearLayoutManager(requireContext())"))
    assertTrue(source("feature/hot/HotFragment.kt").readText().contains("LinearLayoutManager(requireContext())"))
}
```

- [ ] **Step 2: Run the grid contract test to verify it fails**

Run: `./gradlew.bat test --tests "com.aiyifan.app.feature.home.HomeLayoutContractTest"`

Expected: failure because `HomeFragment` still uses `VideoListAdapter` and `LinearLayoutManager`.

- [ ] **Step 3: Add the adapter and change only HomeFragment**

```kotlin
val adapter = HomeVideoAdapter { video ->
    startActivity(VideoPlayerActivity.intent(requireContext(), video.mediaKey))
}
val layoutManager = GridLayoutManager(requireContext(), 2).apply {
    spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int = adapter.spanSizeAt(position)
    }
}
binding.videoRecycler.layoutManager = layoutManager
binding.videoRecycler.adapter = adapter
```

`HomeVideoAdapter.submitList(videos)` must call `HomeFeedItemFactory.create(videos)`, inflate `ItemHomeBannerBinding` for `Banner` and `ItemHomeVideoBinding` for `Card`, return span `2` for banner and `1` for card, clear Glide for blank URLs, and call the supplied video callback from either item root.

- [ ] **Step 4: Run focused tests to verify the integration passes**

Run: `./gradlew.bat test --tests "com.aiyifan.app.feature.home.HomeFeedItemFactoryTest" --tests "com.aiyifan.app.feature.home.HomeLayoutContractTest"`

Expected: all tests pass; hot screen contract confirms it remains a linear list.

- [ ] **Step 5: Commit the integration unit**

```powershell
git add app/src/main/java/com/aiyifan/app/feature/home/HomeVideoAdapter.kt app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt app/src/test/java/com/aiyifan/app/feature/home/HomeLayoutContractTest.kt
git commit -m "feat(app): 首页改为横幅两列展示"
```

### Task 4: Validate Build, Lint, and Device Rendering

**Files:**
- Verify: all files changed in Tasks 1-3.

- [ ] **Step 1: Run the full local unit-test suite**

Run: `./gradlew.bat test`

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [ ] **Step 2: Build and lint the Android app**

Run: `./gradlew.bat assembleDebug lint`

Expected: `BUILD SUCCESSFUL`; the Debug APK is available under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Install and inspect the Debug build on Pixel 6**

Run:

```powershell
D:\SDK\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
D:\SDK\platform-tools\adb.exe shell monkey -p com.aiyifan.app 1
D:\SDK\platform-tools\adb.exe shell screencap -p /sdcard/aiyifan-home.png
D:\SDK\platform-tools\adb.exe pull /sdcard/aiyifan-home.png artifacts\aiyifan-home.png
```

Expected: the selected category has one full-width banner, then two cards per row; card and banner taps open playback; category changes and pull-to-refresh retain the same arrangement.

- [ ] **Step 4: Commit verification-ready implementation**

```powershell
git status --short
git add app/src/main/java/com/aiyifan/app/core/ui/AspectRatioFrameLayout.kt app/src/main/java/com/aiyifan/app/feature/home app/src/main/res/values/attrs.xml app/src/main/res/drawable/bg_home_banner_scrim.xml app/src/main/res/layout/item_home_banner.xml app/src/main/res/layout/item_home_video.xml app/src/test/java/com/aiyifan/app/feature/home
git commit -m "feat(app): 完成首页横幅两列展示"
```

## Self-Review

- Spec coverage: Tasks 1 and 3 preserve the existing source and navigation flow, Task 2 implements the requested visual hierarchy, and Task 4 verifies the approved device experience. Hot remains isolated by Task 3.
- Placeholder scan: no open decisions, deferred work, or unspecified error path remains.
- Type consistency: `HomeFeedItemFactory.create`, `HomeFeedItem.Banner`, `HomeFeedItem.Card`, `HomeVideoAdapter.submitList`, and `HomeVideoAdapter.spanSizeAt` use the same `VideoSummary` model throughout.
