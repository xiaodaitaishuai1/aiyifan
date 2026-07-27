# 视频播放清晰度切换 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在全屏播放器中提供真实的清晰度切换，并在当前播放器会话内跨剧集沿用选择、保留位置和播放状态。

**Architecture:** `PlaybackQualitySelector` 是无 Android 依赖的策略对象，负责从接口档位和会话选择得出目标 `resolution`。远端仓库在指定强制刷新时不复用旧 URL，并优先解析服务器返回的目标档位。`VideoPlayerActivity` 持有会话档位、协调异步解析并用 `PopupMenu` 提供全屏菜单；`VideoPlaybackController` 接收明确的 `shouldPlay` 以恢复暂停状态。

**Tech Stack:** Kotlin、Android View Binding、AndroidX Media3、PopupMenu、JUnit 4、XML。

---

## 文件结构

- Create `app/src/main/java/com/aiyifan/app/feature/video/PlaybackQualitySelector.kt`：清晰度优先级规则。
- Create `app/src/test/java/com/aiyifan/app/feature/video/PlaybackQualitySelectorTest.kt`：会话沿用和回退测试。
- Modify `app/src/main/java/com/aiyifan/app/core/data/CatalogRepository.kt`、`FakeCatalogRepository.kt`、`remote/RemoteCatalogRepository.kt`：支持按目标档位强制刷新地址。
- Create `app/src/test/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepositoryPlaybackTest.kt`：远端解析契约测试。
- Modify `app/src/main/java/com/aiyifan/app/feature/video/VideoPlaybackController.kt` 和其测试：重新准备媒体时恢复播放或暂停。
- Modify `app/src/main/res/layout/activity_video_player.xml`、`app/src/main/res/values/strings.xml`：全屏入口与用户文案。
- Modify `app/src/main/java/com/aiyifan/app/feature/video/PlaybackPresentationPolicy.kt`、`FullScreenControlVisibilityTest.kt`、`VideoPlayerLayoutTest.kt`：入口可见性与布局契约。
- Modify `app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt`：会话状态、菜单、切换和旧源恢复。

### Task 1: 清晰度选择策略

**Files:**
- Create: `app/src/main/java/com/aiyifan/app/feature/video/PlaybackQualitySelector.kt`
- Create: `app/src/test/java/com/aiyifan/app/feature/video/PlaybackQualitySelectorTest.kt`

- [ ] **Step 1: 写入失败的选择优先级测试**

```kotlin
class PlaybackQualitySelectorTest {
    @Test fun `session quality is retained when available`() {
        assertEquals("720P", PlaybackQualitySelector.select(qualities(), "720P", "1080P")?.resolution)
    }

    @Test fun `default quality is used when session quality is unavailable`() {
        assertEquals("1080P", PlaybackQualitySelector.select(qualities(), "4K", "720P")?.resolution)
    }

    @Test fun `episode quality is used when no default exists`() {
        assertEquals("480P", PlaybackQualitySelector.select(listOf(quality("720P"), quality("480P")), null, "480P")?.resolution)
    }

    @Test fun `first usable quality is used as final fallback`() {
        assertEquals("720P", PlaybackQualitySelector.select(listOf(quality("720P"), quality("480P")), null, "4K")?.resolution)
    }

    private fun qualities() = listOf(quality("720P"), quality("1080P", true))
    private fun quality(resolution: String, isDefault: Boolean = false) =
        PlaybackQuality(resolution, resolution, "https://example.com/$resolution.m3u8", isDefault)
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.aiyifan.app.feature.video.PlaybackQualitySelectorTest`

Expected: FAIL，`PlaybackQualitySelector` 未解析。

- [ ] **Step 3: 实现最小策略对象**

```kotlin
object PlaybackQualitySelector {
    fun select(qualities: List<PlaybackQuality>, sessionResolution: String?, episodeResolution: String?): PlaybackQuality? {
        val usable = qualities.filter { it.resolution.isNotBlank() }
        return usable.firstOrNull { it.resolution == sessionResolution }
            ?: usable.firstOrNull(PlaybackQuality::isDefault)
            ?: usable.firstOrNull { it.resolution == episodeResolution }
            ?: usable.firstOrNull()
    }
}
```

导入 `PlaybackQuality`。不要接入任何偏好存储，调用方仅传入内存中的会话选择。

- [ ] **Step 4: 验证策略测试通过**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.aiyifan.app.feature.video.PlaybackQualitySelectorTest`

Expected: PASS，四个优先级测试通过。

- [ ] **Step 5: 提交策略**

```powershell
git add app/src/main/java/com/aiyifan/app/feature/video/PlaybackQualitySelector.kt app/src/test/java/com/aiyifan/app/feature/video/PlaybackQualitySelectorTest.kt
git commit -m "feat(app): 增加播放清晰度选择策略"
```

### Task 2: 按选中清晰度解析远端地址

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/core/data/CatalogRepository.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/data/FakeCatalogRepository.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepository.kt`
- Create: `app/src/test/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepositoryPlaybackTest.kt`

- [ ] **Step 1: 写入强制刷新档位的失败测试**

```kotlin
@Test
fun `forced resolution requests and returns matching playback source`() = runBlocking {
    val fetcher = PlaybackFetcher("""{"data":{"list":[
        {"resolution":"720P","mediaUrl":"https://example.com/720.m3u8"},
        {"resolution":"1080P","mediaUrl":"https://example.com/1080.m3u8","isDefault":true}
    ]}}""")

    val result = repository(fetcher).resolvePlayback(detail(), episode(resolution = "720P"), forceRefresh = true)

    assertEquals("720P", fetcher.requestedResolution)
    assertEquals("720P", result.resolution)
    assertEquals("https://example.com/720.m3u8", result.mediaUrl)
}

@Test
fun `forced resolution does not fall back to stale direct url on request failure`() = runBlocking {
    val result = repository(PlaybackFetcher("{}", responseCode = 500))
        .resolvePlayback(detail(), episode(mediaUrl = "https://example.com/old.m3u8"), forceRefresh = true)

    assertNull(result.mediaUrl)
}
```

`PlaybackFetcher` 返回配置地址和播放响应，并从 `api/Video/getPlayData` URL 的 `resolution` query 记录 `requestedResolution`。`detail()` 使用 `mediaKey = "media-1"`、`videoType = 1`；`episode()` 使用 `uniqueId = 3`。

- [ ] **Step 2: 运行测试确认新契约缺失**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryPlaybackTest`

Expected: FAIL，`resolvePlayback` 没有 `forceRefresh` 参数。

- [ ] **Step 3: 扩展仓库契约并实现请求选择**

将接口与两个实现统一为：

```kotlin
suspend fun resolvePlayback(
    detail: VideoDetail,
    episode: Episode,
    forceRefresh: Boolean = false,
): Episode
```

只有 `!forceRefresh && !episode.mediaUrl.isNullOrBlank()` 时直接返回旧集。强制刷新时继续调用 `getPlayData`，携带 `episode.resolution.orEmpty()`。

将 `parsePlayableEpisode` 改为接收 `requestedResolution`，使用 `for (index in 0 until items.length())` 收集有 `mediaUrl` 的条目，再按目标 `resolution`、`isDefault`、第一个可用项的顺序选择。请求或解析异常时，普通解析返回传入集；强制刷新返回 `episode.copy(mediaUrl = null)`，以便调用方恢复旧源。`FakeCatalogRepository` 接收但忽略此参数。

- [ ] **Step 4: 运行远端回归测试**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryPlaybackTest --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryHomeRefreshTest --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositorySearchTest`

Expected: PASS，目标 URL 被选择，强制刷新失败不泄漏旧 URL，既有远端目录与搜索测试通过。

- [ ] **Step 5: 提交远端解析**

```powershell
git add app/src/main/java/com/aiyifan/app/core/data/CatalogRepository.kt app/src/main/java/com/aiyifan/app/core/data/FakeCatalogRepository.kt app/src/main/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepository.kt app/src/test/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepositoryPlaybackTest.kt
git commit -m "feat(app): 按清晰度刷新视频播放地址"
```

### Task 3: 恢复切换前的播放状态

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/feature/video/VideoPlaybackController.kt`
- Modify: `app/src/test/java/com/aiyifan/app/feature/video/VideoPlaybackControllerTest.kt`

- [ ] **Step 1: 写入暂停状态恢复的失败测试**

```kotlin
@Test
fun `prepare keeps playback paused when requested`() {
    val engine = FakePlaybackEngine()
    val controller = VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession())

    assertTrue(controller.prepare(sampleDetail(), sampleEpisode(), startPositionMs = 12_345L, shouldPlay = false))

    assertEquals(12_345L, engine.seekPositionMs)
    assertFalse(engine.isPlaying)
}
```

- [ ] **Step 2: 运行测试确认参数缺失**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.aiyifan.app.feature.video.VideoPlaybackControllerTest`

Expected: FAIL，`prepare` 没有 `shouldPlay` 参数。

- [ ] **Step 3: 实现播放或暂停恢复**

```kotlin
fun prepare(detail: VideoDetail, episode: Episode, startPositionMs: Long = 0L, shouldPlay: Boolean = true): Boolean {
    val mediaUrl = episode.mediaUrl?.trim().orEmpty()
    if (released || mediaUrl.isBlank()) return false
    engine.setMediaUrl(mediaUrl)
    engine.prepare()
    if (startPositionMs > 0L) engine.seekTo(startPositionMs)
    if (shouldPlay) engine.play() else engine.pause()
    activeDetail = detail
    activeEpisode = episode
    return true
}
```

已有调用保持默认自动播放；不让 Activity 直接操作 `PlaybackEngine`。

- [ ] **Step 4: 验证控制器测试通过**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.aiyifan.app.feature.video.VideoPlaybackControllerTest`

Expected: PASS，既有自动播放、历史记录、释放和新增暂停恢复测试通过。

- [ ] **Step 5: 提交播放状态恢复**

```powershell
git add app/src/main/java/com/aiyifan/app/feature/video/VideoPlaybackController.kt app/src/test/java/com/aiyifan/app/feature/video/VideoPlaybackControllerTest.kt
git commit -m "feat(app): 切换播放源时保留暂停状态"
```

### Task 4: 全屏菜单与会话切换

**Files:**
- Modify: `app/src/main/res/layout/activity_video_player.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/aiyifan/app/feature/video/PlaybackPresentationPolicy.kt`
- Modify: `app/src/test/java/com/aiyifan/app/feature/video/FullScreenControlVisibilityTest.kt`
- Modify: `app/src/test/java/com/aiyifan/app/feature/video/VideoPlayerLayoutTest.kt`
- Modify: `app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt`

- [ ] **Step 1: 写入入口可见性与布局的失败测试**

```kotlin
@Test
fun `quality button is visible only for visible full screen controls with choices`() {
    assertTrue(FullScreenControlVisibility.shouldShowQualityButton(true, View.VISIBLE, 2))
    assertFalse(FullScreenControlVisibility.shouldShowQualityButton(true, View.GONE, 2))
    assertFalse(FullScreenControlVisibility.shouldShowQualityButton(false, View.VISIBLE, 2))
    assertFalse(FullScreenControlVisibility.shouldShowQualityButton(true, View.VISIBLE, 1))
}

@Test
fun `quality button overlays player and starts hidden`() {
    val button = viewWithId(viewWithId(videoPlayerLayout(), "playerContainer")!!, "fullScreenQualityButton")!!
    assertEquals("gone", button.getAttribute("android:visibility"))
    assertEquals("bottom|end", button.getAttribute("android:layout_gravity"))
}
```

- [ ] **Step 2: 运行测试确认入口尚不存在**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.aiyifan.app.feature.video.FullScreenControlVisibilityTest --tests com.aiyifan.app.feature.video.VideoPlayerLayoutTest`

Expected: FAIL，缺少 `shouldShowQualityButton` 与 `fullScreenQualityButton`。

- [ ] **Step 3: 添加入口与可测试的可见性规则**

在 `playerContainer` 内、`fullscreenExitButton` 后添加：

```xml
<Button
    android:id="@+id/fullScreenQualityButton"
    android:layout_width="wrap_content"
    android:layout_height="40dp"
    android:layout_gravity="bottom|end"
    android:layout_margin="12dp"
    android:background="@drawable/bg_floating_control"
    android:contentDescription="@string/video_quality"
    android:minWidth="0dp"
    android:paddingHorizontal="12dp"
    android:textColor="@color/white"
    android:textSize="14sp"
    android:visibility="gone" />
```

在 `FullScreenControlVisibility` 添加：

```kotlin
fun shouldShowQualityButton(isFullScreen: Boolean, controllerVisibility: Int, availableQualityCount: Int): Boolean =
    isFullScreen && controllerVisibility == View.VISIBLE && availableQualityCount > 1
```

在 `strings.xml` 添加 `video_quality`、`video_quality_switched`（`已切换至 %1$s`）和 `video_quality_switch_failed`（`清晰度切换失败，请重试`）。

- [ ] **Step 4: 编排 Activity 会话状态和切换恢复**

新增仅内存状态：

```kotlin
private var selectedQualityResolution: String? = null
private var isQualitySwitching = false
```

首次播放和切集时，调用 `PlaybackQualitySelector.select(detail.qualities, selectedQualityResolution, episode.resolution)`；将选中的档位复制进待解析 `Episode`。当选中档位与剧集声明不同，使用 `forceRefresh = true`；解析的实际 `resolution` 与请求不同但 URL 可用时，以实际值更新会话选择。缺少 URL 时，以默认档位重试一次，仍失败则显示已有播放源加载失败提示。

点击 `fullScreenQualityButton` 时，用 `PopupMenu(this, binding.fullScreenQualityButton)` 添加所有非空档位描述。调用 `menu.setGroupCheckable(0, true, true)`，将当前档位设为 checked；当前项被点击时仅关闭菜单，其余项调用 `switchQuality(quality)`。

`switchQuality` 先保存旧状态：

```kotlin
val previousEpisode = playingEpisode ?: return
val previousResolution = selectedQualityResolution
val positionMs = playbackController.currentPositionMs
val wasPlaying = playbackController.isPlaying
```

禁用入口并设置 `isQualitySwitching`。用 `previousEpisode.copy(resolution = quality.resolution, mediaUrl = null)` 和 `forceRefresh = true` 解析；仅在结果有 URL 且 `prepare(detail, resolved, positionMs, wasPlaying)` 成功时提交 `playingEpisode` 与 `selectedQualityResolution`，显示成功提示。失败时调用 `prepare(detail, previousEpisode, positionMs, wasPlaying)` 恢复旧源和旧选择，显示失败提示。`finally` 中恢复按钮可用性和可见性。

从全屏切换和 `ControllerVisibilityListener` 后调用 `updateFullScreenQualityButton()`；它使用 `shouldShowQualityButton` 控制入口并设置当前档位文字。小窗、退出全屏、Activity 销毁均不清除会话档位，以保证本 Activity 后续切集沿用；新 Activity 实例自然重置。

- [ ] **Step 5: 运行视频功能回归测试**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.aiyifan.app.feature.video.PlaybackQualitySelectorTest --tests com.aiyifan.app.feature.video.VideoPlaybackControllerTest --tests com.aiyifan.app.feature.video.FullScreenControlVisibilityTest --tests com.aiyifan.app.feature.video.VideoPlayerLayoutTest --tests com.aiyifan.app.feature.video.VideoPlayerBackBehaviorTest`

Expected: PASS，策略、播放恢复、菜单入口和既有返回行为通过。

- [ ] **Step 6: 提交菜单与会话切换**

```powershell
git add app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt app/src/main/java/com/aiyifan/app/feature/video/PlaybackPresentationPolicy.kt app/src/main/res/layout/activity_video_player.xml app/src/main/res/values/strings.xml app/src/test/java/com/aiyifan/app/feature/video/FullScreenControlVisibilityTest.kt app/src/test/java/com/aiyifan/app/feature/video/VideoPlayerLayoutTest.kt
git commit -m "feat(app): 支持播放页清晰度切换"
```

### Task 5: 完整验证

**Files:**
- Verify: `app/src/main/java/com/aiyifan/app/feature/video/PlaybackQualitySelector.kt`
- Verify: `app/src/main/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepository.kt`
- Verify: `app/src/main/java/com/aiyifan/app/feature/video/VideoPlaybackController.kt`
- Verify: `app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt`
- Verify: `app/src/main/res/layout/activity_video_player.xml`

- [ ] **Step 1: 运行全部本地单元测试**

Run: `./gradlew.bat test`

Expected: `BUILD SUCCESSFUL`，没有失败测试。

- [ ] **Step 2: 运行 Android Lint**

Run: `./gradlew.bat lint`

Expected: `BUILD SUCCESSFUL`，不产生新的 lint error。

- [ ] **Step 3: 构建调试 APK**

Run: `./gradlew.bat assembleDebug`

Expected: `BUILD SUCCESSFUL`，APK 输出至 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 4: 手动验证真实播放链路**

在有多个档位的视频进入全屏，确认控制栏显示当前档位且竖屏不显示。切换到另一档位，确认时间位置和播放或暂停状态保持；再切换剧集，确认本次选择被请求。退出页面重新进入，确认恢复接口默认档位而非上次会话选择。断开播放接口或选择无地址档位时，确认旧媒体继续播放、旧档位标签保持，并出现失败提示。
