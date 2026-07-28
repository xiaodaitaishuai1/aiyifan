# Homepage Cross-Category Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep seven initial home items including the banner and append six unique cross-category items when the user reaches the end of the list.

**Architecture:** `HomeFeedPagination` is a pure Kotlin state holder that builds a current-category-first, `mediaKey`-deduplicated sequence and exposes initial, next, and reset slices. `HomeFragment` loads each cached category list once per reset, submits the current slice to the existing adapter, and uses a RecyclerView scroll listener to append the next slice.

**Tech Stack:** Kotlin, Android Views, RecyclerView, Coroutines, JUnit 4.

---

### Task 1: Test and Implement the Pagination Model

**Files:**
- Create: `app/src/test/java/com/aiyifan/app/feature/home/HomeFeedPaginationTest.kt`
- Create: `app/src/main/java/com/aiyifan/app/feature/home/HomeFeedPagination.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `initial page keeps selected category first and fills to seven uniquely`() {
    val pager = HomeFeedPagination(initialSize = 7, pageSize = 6)
    val page = pager.reset(
        selected = listOf(video("a"), video("b"), video("c"), video("d"), video("e")),
        supplements = listOf(listOf(video("c"), video("f"), video("g"), video("h"))),
    )

    assertEquals(listOf("a", "b", "c", "d", "e", "f", "g"), page.map { it.mediaKey })
}

@Test
fun `next page appends six remaining items and stops at the end`() {
    val pager = HomeFeedPagination(initialSize = 7, pageSize = 6)
    pager.reset((1..15).map { video("v$it") }, emptyList())

    assertEquals((1..13).map { "v$it" }, pager.next().map { it.mediaKey })
    assertTrue(pager.hasMore)
    assertEquals((1..15).map { "v$it" }, pager.next().map { it.mediaKey })
    assertFalse(pager.hasMore)
}
```

- [ ] **Step 2: Verify red**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.aiyifan.app.feature.home.HomeFeedPaginationTest"`

Expected: compilation failure because `HomeFeedPagination` does not exist.

- [ ] **Step 3: Implement the model**

```kotlin
class HomeFeedPagination(
    private val initialSize: Int = 7,
    private val pageSize: Int = 6,
) {
    private var allItems = emptyList<VideoSummary>()
    private var shownCount = 0

    val hasMore: Boolean get() = shownCount < allItems.size

    fun reset(selected: List<VideoSummary>, supplements: List<List<VideoSummary>>): List<VideoSummary> {
        allItems = (selected + supplements.flatten()).distinctBy(VideoSummary::mediaKey)
        shownCount = minOf(initialSize, allItems.size)
        return allItems.take(shownCount)
    }

    fun next(): List<VideoSummary> {
        shownCount = minOf(shownCount + pageSize, allItems.size)
        return allItems.take(shownCount)
    }
}
```

- [ ] **Step 4: Verify green**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.aiyifan.app.feature.home.HomeFeedPaginationTest"`

Expected: both tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/aiyifan/app/feature/home/HomeFeedPagination.kt app/src/test/java/com/aiyifan/app/feature/home/HomeFeedPaginationTest.kt
git commit -m "feat(app): 增加首页跨分类分页模型"
```

### Task 2: Connect Pagination to Home Scrolling

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt`
- Modify: `app/src/test/java/com/aiyifan/app/feature/home/HomeLayoutContractTest.kt`

- [ ] **Step 1: Add a failing integration contract**

```kotlin
@Test
fun `home fragment loads cross category pages when the grid reaches its end`() {
    val source = source("feature/home/HomeFragment.kt").readText()
    assertTrue(source.contains("HomeFeedPagination()"))
    assertTrue(source.contains("canScrollVertically(1)"))
    assertTrue(source.contains("adapter.submitList(pagination.next())"))
}
```

- [ ] **Step 2: Verify red**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.aiyifan.app.feature.home.HomeLayoutContractTest"`

Expected: the new contract fails because `HomeFragment` has no pagination state or scroll listener.

- [ ] **Step 3: Add reset and end-of-list loading**

```kotlin
private val pagination = HomeFeedPagination()

private suspend fun resetHomeFeed(categories: List<Category>, selected: Category) {
    val selectedVideos = repository.getHomeVideos(selected.id)
    val supplements = categories.filterNot { it.id == selected.id }
        .map { repository.getHomeVideos(it.id) }
    adapter.submitList(pagination.reset(selectedVideos, supplements))
}

binding.videoRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        if (dy > 0 && !recyclerView.canScrollVertically(1) && pagination.hasMore) {
            adapter.submitList(pagination.next())
        }
    }
})
```

Call `resetHomeFeed` from initial load, category selection, refresh success, and non-refresh failure with an empty selected list. Do not alter `HomeVideoAdapter`, remote endpoints, or the hot screen.

- [ ] **Step 4: Verify green**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.aiyifan.app.feature.home.HomeFeedPaginationTest" --tests "com.aiyifan.app.feature.home.HomeLayoutContractTest"`

Expected: all pagination and layout contracts pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt app/src/test/java/com/aiyifan/app/feature/home/HomeLayoutContractTest.kt
git commit -m "feat(app): 首页支持跨分类加载更多"
```

### Task 3: Verify the User Flow

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew.bat test`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build and lint**

Run: `./gradlew.bat assembleDebug lint`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install on Pixel 6**

Run: `D:\SDK\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`

Expected: selected category shows seven items initially; scrolling to the bottom appends six unique items when available.

## Self-Review

- Spec coverage: Task 1 implements current-first ordering, de-duplication, initial seven, six-item pages, no-more state, and reset. Task 2 attaches those state transitions only to the home screen. Task 3 verifies the requested device behavior.
- Placeholder scan: no deferred decisions or unspecified data source remains.
- Type consistency: all pagination methods return `List<VideoSummary>` and `HomeVideoAdapter.submitList` accepts the same type.
