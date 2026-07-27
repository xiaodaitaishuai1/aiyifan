# Theme Polish and Empty State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make system bars, text, buttons, dialogs, images, Mine, and empty states visually consistent in light and dark themes.

**Architecture:** Theme resources provide semantic surfaces and text colors; `setupEdgeToEdge` uses the resolved surface for both system bars. XML defines shared rounded backgrounds and an overlay empty-state container. Content images use Glide's rounded-corners transformation.

**Tech Stack:** Kotlin, AndroidX, Material Components, View Binding, Glide, JUnit 4.

---

### Task 1: System Bars and Text Contracts

**Files:**
- Create: `app/src/test/java/com/aiyifan/app/core/ui/ThemeSurfaceContractTest.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/ui/Insets.kt`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-night/colors.xml`

- [ ] Write tests requiring both palettes to define `surface`, `text_primary`, `text_secondary`, and `text_on_accent`.
- [ ] Verify the test fails before `text_on_accent` exists.
- [ ] Add `text_on_accent`, use the resolved `surface` color for both system bars, and disable API 29+ contrast enforcement.
- [ ] Run `:app:testDebugUnitTest --tests com.aiyifan.app.core.ui.ThemeSurfaceContractTest`.

### Task 2: Rounded Interactive and Image Content

**Files:**
- Create: `app/src/test/java/com/aiyifan/app/core/ui/RoundedContentContractTest.kt`
- Modify: `app/src/main/res/drawable/bg_button_primary.xml`
- Modify: `app/src/main/res/drawable/bg_button_outline.xml`
- Modify: `app/src/main/res/drawable/bg_poster.xml`
- Modify: `app/src/main/res/layout/item_video_card.xml`
- Modify: `app/src/main/res/layout/item_search_result.xml`
- Modify: `app/src/main/java/com/aiyifan/app/core/ui/VideoListAdapter.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/ui/SearchAdapters.kt`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/values-night/styles.xml`

- [ ] Write tests requiring 8dp button/poster radii and rounded Glide poster requests.
- [ ] Verify the tests fail with existing 4dp and untransformed loads.
- [ ] Apply 8dp drawables, 44dp critical text buttons, rounded poster transforms, and rounded Material dialog theme overlay.
- [ ] Run focused contract and layout tests.

### Task 3: Mine and Empty States

**Files:**
- Modify: `app/src/test/java/com/aiyifan/app/feature/mine/MineLayoutTest.kt`
- Modify: `app/src/main/res/layout/fragment_mine.xml`
- Modify: `app/src/main/java/com/aiyifan/app/feature/mine/MineFragment.kt`
- Modify: `app/src/main/res/layout/activity_simple_list.xml`
- Create: `app/src/main/res/drawable/ic_empty_history.xml`
- Create: `app/src/test/java/com/aiyifan/app/feature/history/EmptyStateLayoutTest.kt`

- [ ] Write failing tests that reject the Mine profile/login views and require the empty state to be centered with an image.
- [ ] Verify the tests fail against the existing layouts.
- [ ] Remove the Mine guest/login card and listener. Overlay centered image and text above the list in the shared simple-list layout.
- [ ] Run Mine and empty-state layout tests.

### Task 4: Verify and Deliver

- [ ] Run `./gradlew.bat test lint assembleDebug -x buildLibboxAar`.
- [ ] Install the debug APK and verify light/dark system bar pixels, visible button labels, rounded posters, Mine without login, and centered empty state.
- [ ] Commit with a Chinese Conventional Commit message, merge to `master`, run final verification on `master`, and push `master`.
