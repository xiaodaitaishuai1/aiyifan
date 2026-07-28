# Homepage Refresh Animation and Image Loading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add theme-aware pull-to-refresh and load-more animation feedback while improving perceived homepage poster loading.

**Architecture:** `HomeFragment` owns a footer state in the same RecyclerView adapter used for banner and cards. `HomeVideoAdapter` exposes a loading row that spans both grid columns. Poster binding uses a shared Glide request with theme-aware placeholders, cache strategy, priority, decoded dimensions, and fade-in.

**Tech Stack:** Kotlin, Android Views, RecyclerView, SwipeRefreshLayout, Glide, JUnit 4.

---

### Task 1: Add a Failing Home UI Contract

**Files:**
- Modify: `app/src/test/java/com/aiyifan/app/feature/home/HomeFragmentPaginationContractTest.kt`
- Modify: `app/src/test/java/com/aiyifan/app/feature/home/HomeFeedItemFactoryTest.kt`

- [ ] Write tests requiring a two-column-spanning load-more item, a theme color assignment for `homeRefresh`, and adapter loading state forwarding.
- [ ] Run the focused tests and verify they fail because no load-more item or refresh colors exist.
- [ ] Implement the minimal loading item model and theme color assignment.
- [ ] Re-run focused tests and verify they pass.

### Task 2: Add Theme-Aware Footer Layout and Image Requests

**Files:**
- Create: `app/src/main/res/layout/item_home_loading.xml`
- Modify: `app/src/main/java/com/aiyifan/app/feature/home/HomeVideoAdapter.kt`
- Modify: `app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt`

- [ ] Add a fixed-height footer containing only an indeterminate `ProgressBar` tinted with `accent`.
- [ ] Submit the loading state before a next-page request and remove it in both success and failure paths.
- [ ] Configure poster requests with `bg_poster` placeholder/error drawables, `DiskCacheStrategy.ALL`, high priority, target-size override, and 160ms cross-fade.
- [ ] Verify first card remains a full-width banner, normal cards remain one column, and loading spans both columns.

### Task 3: Verify

- [ ] Run focused unit tests.
- [ ] Run `./gradlew.bat test`.
- [ ] Run `./gradlew.bat assembleDebug lint`.
- [ ] Install Debug APK and verify refresh and footer indicators in light and dark themes, including a slow-image cold start.
