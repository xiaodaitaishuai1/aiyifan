# Homepage Current-Category Network Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load the selected home category from the captured production API and append only that category's subsequent server pages.

**Architecture:** `CatalogRepository` exposes a `HomeVideoPage` with server-provided videos and an end-of-list flag. `RemoteCatalogRepository` obtains category IDs from `api/List/NavigationBar`, posts JSON to `api/Home/GetRelativeVideos`, and filters response sections by the selected category name. `HomeFragment` owns the selected category and page state, appending successful responses without mixing categories.

**Tech Stack:** Kotlin, Android Views, RecyclerView, Coroutines, `HttpURLConnection`, JUnit 4.

---

### Task 1: Define and Test the Remote Page Contract

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/core/data/CatalogRepository.kt`
- Create: `app/src/main/java/com/aiyifan/app/core/data/HomeVideoPage.kt`
- Modify: `app/src/test/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepositoryHomeRefreshTest.kt`

- [x] Write a failing test where the movie category has ID `3`, page `2` is posted as JSON, and only the response section named `电影` is returned.
- [x] Run the focused test and confirm it fails because the page API is absent.
- [x] Add `HomeVideoPage` and a page-loading repository method.
- [x] Re-run the focused test and confirm it passes.

### Task 2: Fetch Navigation and Server Pages

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/core/data/remote/RemoteConfigResolver.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepository.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/data/remote/TripDataHomeParser.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/data/FakeCatalogRepository.kt`

- [x] Add JSON POST support to `HttpFetcher` and `UrlConnectionHttpFetcher`.
- [x] Parse `categoryId`, `name`, `type`, and `styleType` from `api/List/NavigationBar`.
- [x] Post `{ "page": "N", "size": "30", "titleid": "categoryId" }` to `api/Home/GetRelativeVideos`.
- [x] Return a non-terminal page on a non-empty matching section, deduplicated by `mediaKey`; return a terminal empty page when that section is absent or empty.
- [x] Preserve the currently visible home data in the UI when a later page request fails.

### Task 3: Bind Scroll State to the Current Category

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/feature/home/HomeFeedPagination.kt`
- Modify: `app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt`
- Modify: `app/src/test/java/com/aiyifan/app/feature/home/HomeFeedPaginationTest.kt`

- [x] Replace fixed local slices with page number, accumulated unique videos, loading, and terminal state.
- [x] Load page `1` after initial load, refresh, or category selection.
- [x] Load only `nextPage` when the selected category reaches the end; ignore stale completion after category selection changes.
- [x] Show `没有更多了` only after an empty page for that category; never request another category to fill the grid.

### Task 4: Verify

- [x] Run focused repository and pagination unit tests.
- [x] Run `./gradlew.bat test`.
- [x] Run `./gradlew.bat assembleDebug` and `./gradlew.bat lint`.
- [x] Install the debug APK and confirm one category displays its first server page and subsequent scrolls issue pages `2`, `3`, and `4` for the same `titleid`.
