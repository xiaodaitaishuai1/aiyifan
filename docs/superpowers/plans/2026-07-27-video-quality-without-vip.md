# 无 VIP 限制的视频清晰度选择 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在全屏播放时展示服务端返回的清晰度，并允许普通用户切换所有已返回的播放源。

**Architecture:** 新增 `ResolvedPlayback` 数据对象，将一次播放请求选中的 `Episode` 与该响应内全部有效 `PlaybackQuality` 一起返回。`RemoteCatalogRepository` 只根据接口数据生成清晰度，`VideoPlayerActivity` 将最新清晰度保存在当前 `VideoDetail`，不引入 VIP 字段或权限判断。

**Tech Stack:** Kotlin、AndroidX Media3、JUnit 4、Kotlin Coroutines、`org.json`。

---

## 文件结构

- 修改 `app/src/main/java/com/aiyifan/app/core/model/Models.kt`：声明数据层返回的 `ResolvedPlayback`。
- 修改 `app/src/main/java/com/aiyifan/app/core/data/CatalogRepository.kt`：将播放解析契约改为返回 `ResolvedPlayback`。
- 修改 `app/src/main/java/com/aiyifan/app/core/data/FakeCatalogRepository.kt`：使本地回退路径保留详情中的全部清晰度。
- 修改 `app/src/main/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepository.kt`：从 `getPlayData.data.list` 解析当前播放条目及全部有效清晰度。
- 修改 `app/src/main/java/com/aiyifan/app/feature/video/PlaybackPresentationPolicy.kt`：允许一档清晰度时显示全屏清晰度按钮。
- 新建 `app/src/main/java/com/aiyifan/app/feature/video/PlaybackQualitySession.kt`：集中处理服务端清晰度覆盖与详情回退。
- 修改 `app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt`：接收播放结果、更新当前详情清晰度，单档时不发起切换。
- 修改 `app/src/test/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepositoryPlaybackTest.kt`：覆盖多档播放响应和无 VIP 筛选。
- 修改 `app/src/test/java/com/aiyifan/app/feature/video/FullScreenControlVisibilityTest.kt`：覆盖单档清晰度按钮显示。
- 新建 `app/src/test/java/com/aiyifan/app/feature/video/PlaybackQualitySessionTest.kt`：覆盖播放响应的清晰度合并规则。

### Task 1: 定义播放结果契约

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/core/model/Models.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/data/CatalogRepository.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/data/FakeCatalogRepository.kt`
- Test: `app/src/test/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepositoryPlaybackTest.kt`

- [ ] **Step 1: 写入失败测试，要求播放结果同时暴露当前剧集与所有服务端档位**

```kotlin
assertEquals("720P", result.episode.resolution)
assertEquals(listOf("720P", "1080P"), result.qualities.map(PlaybackQuality::resolution))
```

在 `forced resolution requests and returns matching playback source` 中将旧的 `result.resolution` 与 `result.mediaUrl` 断言改为 `result.episode` 字段断言，并新增上面的清晰度列表断言。

- [ ] **Step 2: 运行测试并确认因返回类型尚未变更而失败**

Run: `./gradlew.bat test --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryPlaybackTest`

Expected: FAIL，编译错误指出 `Episode` 没有 `episode` 或 `qualities` 属性。

- [ ] **Step 3: 添加最小数据对象并更新仓库契约**

在 `Models.kt` 的 `PlaybackQuality` 后添加：

```kotlin
data class ResolvedPlayback(
    val episode: Episode,
    val qualities: List<PlaybackQuality> = emptyList(),
)
```

将 `CatalogRepository.resolvePlayback` 的返回类型改为 `ResolvedPlayback`。在 `FakeCatalogRepository` 中返回：

```kotlin
ResolvedPlayback(
    episode = episode,
    qualities = detail.qualities,
)
```

- [ ] **Step 4: 运行测试确认契约已编译，并记录远程解析仍待实现的失败**

Run: `./gradlew.bat test --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryPlaybackTest`

Expected: FAIL，断言显示远程实现尚未返回 `qualities`。

### Task 2: 从播放响应解析全部清晰度

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepository.kt`
- Test: `app/src/test/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepositoryPlaybackTest.kt`

- [ ] **Step 1: 增加失败测试，证明所有服务端条目均可被普通用户使用**

将播放响应扩展为：

```json
{"data":{"list":[
  {"resolution":"720P","mediaUrl":"https://example.com/720.m3u8"},
  {"resolution":"1080P","mediaUrl":"https://example.com/1080.m3u8","isVip":true}
]}}
```

新增断言：

```kotlin
assertEquals(listOf("720P", "1080P"), result.qualities.map(PlaybackQuality::resolution))
assertEquals("https://example.com/1080.m3u8", result.qualities.last().mediaUrl)
```

- [ ] **Step 2: 运行测试并确认 `isVip` 条目当前未被收集**

Run: `./gradlew.bat test --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryPlaybackTest`

Expected: FAIL，期望的两档清晰度与实际结果不一致。

- [ ] **Step 3: 用 `ResolvedPlayback` 替换远程播放解析结果**

将 `parsePlayableEpisode` 改为 `parseResolvedPlayback`。它必须：

```kotlin
val playable = items.mapNotNull { item ->
    val resolution = item.optionalRemoteText("resolution") ?: return@mapNotNull null
    val mediaUrl = item.optionalRemoteText("mediaUrl") ?: return@mapNotNull null
    item to PlaybackQuality(
        resolution = resolution,
        description = "${resolution}P",
        mediaUrl = mediaUrl,
        isDefault = item.optBoolean("isDefault"),
    )
}
```

选择 `requestedResolution`、默认项或第一项作为 `episode`，并返回：

```kotlin
ResolvedPlayback(
    episode = fallbackEpisode.copy(
        episodeKey = chosenItem.optionalRemoteText("episodeKey") ?: fallbackEpisode.episodeKey,
        uniqueId = chosenItem.optInt("episodeId", fallbackEpisode.uniqueId),
        mediaUrl = chosenQuality.mediaUrl,
        resolution = chosenQuality.resolution,
        lang = chosenItem.optionalRemoteText("lang") ?: fallbackEpisode.lang,
    ),
    qualities = playable.map { it.second }.distinctBy(PlaybackQuality::resolution),
)
```

`resolvePlayback` 的提前返回和异常回退也改为 `ResolvedPlayback`；回退时使用 `detail.qualities`，不检查、存储或过滤 `isVip`。

- [ ] **Step 4: 运行远程仓库测试确认通过**

Run: `./gradlew.bat test --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryPlaybackTest`

Expected: PASS，包含 `isVip: true` 的服务端条目仍在结果列表中。

- [ ] **Step 5: 提交数据层改动**

```powershell
git add app/src/main/java/com/aiyifan/app/core/model/Models.kt app/src/main/java/com/aiyifan/app/core/data/CatalogRepository.kt app/src/main/java/com/aiyifan/app/core/data/FakeCatalogRepository.kt app/src/main/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepository.kt app/src/test/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepositoryPlaybackTest.kt
git commit -m "feat(app): 返回全部可用播放清晰度"
```

### Task 3: 一档清晰度时显示全屏入口

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/feature/video/PlaybackPresentationPolicy.kt`
- Test: `app/src/test/java/com/aiyifan/app/feature/video/FullScreenControlVisibilityTest.kt`

- [ ] **Step 1: 修改失败测试，声明一档清晰度也显示按钮**

将最后一个断言替换为：

```kotlin
assertTrue(FullScreenControlVisibility.shouldShowQualityButton(true, View.VISIBLE, 1))
assertFalse(FullScreenControlVisibility.shouldShowQualityButton(true, View.VISIBLE, 0))
```

- [ ] **Step 2: 运行测试并确认旧条件拒绝单档**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.video.FullScreenControlVisibilityTest`

Expected: FAIL，`shouldShowQualityButton(true, View.VISIBLE, 1)` 返回 `false`。

- [ ] **Step 3: 放宽可见性条件**

将函数返回值改为：

```kotlin
isFullScreen && controllerVisibility == View.VISIBLE && availableQualityCount > 0
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.video.FullScreenControlVisibilityTest`

Expected: PASS。

### Task 4: 将播放结果同步到清晰度菜单

**Files:**
- Create: `app/src/main/java/com/aiyifan/app/feature/video/PlaybackQualitySession.kt`
- Modify: `app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt`
- Create: `app/src/test/java/com/aiyifan/app/feature/video/PlaybackQualitySessionTest.kt`

- [ ] **Step 1: 写入失败测试，优先保留接口返回的全部清晰度**

创建 `PlaybackQualitySessionTest.kt`：

```kotlin
@Test
fun `server qualities replace detail qualities without membership filtering`() {
    val detailQualities = listOf(quality("720P"))
    val responseQualities = listOf(quality("720P"), quality("1080P"))

    assertEquals(
        listOf("720P", "1080P"),
        PlaybackQualitySession.merge(detailQualities, responseQualities).map(PlaybackQuality::resolution),
    )
}

@Test
fun `detail qualities remain available when response has no valid qualities`() {
    assertEquals(
        listOf("720P"),
        PlaybackQualitySession.merge(listOf(quality("720P")), emptyList()).map(PlaybackQuality::resolution),
    )
}
```

- [ ] **Step 2: 运行测试并确认因策略尚不存在而失败**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.video.PlaybackQualitySessionTest`

Expected: FAIL，编译错误指出 `PlaybackQualitySession` 未定义。

- [ ] **Step 3: 实现最小清晰度合并策略**

创建：

```kotlin
package com.aiyifan.app.feature.video

import com.aiyifan.app.core.model.PlaybackQuality

object PlaybackQualitySession {
    fun merge(
        detailQualities: List<PlaybackQuality>,
        responseQualities: List<PlaybackQuality>,
    ): List<PlaybackQuality> = responseQualities.ifEmpty { detailQualities }
}
```

- [ ] **Step 4: 运行策略测试确认通过**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.video.PlaybackQualitySessionTest`

Expected: PASS。

- [ ] **Step 5: 将 `VideoPlayerActivity` 的播放解析改为消费 `ResolvedPlayback`**

将 `resolvePlaybackForSession` 返回类型改为 `ResolvedPlayback`，并在每次解析成功后使用：

```kotlin
private fun updateSessionQualities(detail: VideoDetail, result: ResolvedPlayback): VideoDetail =
    detail.copy(qualities = PlaybackQualitySession.merge(detail.qualities, result.qualities))
```

在 `loadEpisodePlayback` 中先调用该函数更新 `detail`，再将 `result.episode` 传给 `playbackController.prepare`。在 `switchQuality` 中同样用结果更新 `detail`，并以 `result.episode` 恢复播放位置和状态。

在 `showQualityMenu` 中仅对空列表提前返回：

```kotlin
if (isQualitySwitching || qualities.isEmpty()) return
```

单档菜单不会触发 `switchQuality`，因为唯一条目已等于 `selectedQualityResolution`。

- [ ] **Step 6: 编译并运行视频相关测试**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.video.PlaybackQualitySessionTest --tests com.aiyifan.app.feature.video.FullScreenControlVisibilityTest --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryPlaybackTest`

Expected: PASS。

- [ ] **Step 7: 提交播放页改动**

```powershell
git add app/src/main/java/com/aiyifan/app/feature/video/PlaybackPresentationPolicy.kt app/src/main/java/com/aiyifan/app/feature/video/PlaybackQualitySession.kt app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt app/src/test/java/com/aiyifan/app/feature/video/FullScreenControlVisibilityTest.kt app/src/test/java/com/aiyifan/app/feature/video/PlaybackQualitySessionTest.kt
git commit -m "feat(app): 普通用户可选择播放清晰度"
```

### Task 5: 全量验证

**Files:**
- Verify only: `app/src/main/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepository.kt`
- Verify only: `app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt`

- [ ] **Step 1: 运行本地单元测试**

Run: `./gradlew.bat test`

Expected: PASS，生成 `app/build/reports/tests/` 报告。

- [ ] **Step 2: 运行 Android Lint**

Run: `./gradlew.bat lint`

Expected: BUILD SUCCESSFUL，生成 `app/build/reports/` 报告。

- [ ] **Step 3: 构建调试 APK**

Run: `./gradlew.bat assembleDebug`

Expected: BUILD SUCCESSFUL，生成 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 4: 在设备上进行验收**

1. 打开任意可播放视频并进入全屏，轻触视频显示控制栏。
2. 确认当前分辨率按钮可见；单档时按钮显示当前档位。
3. 对返回多档的影片，确认菜单显示全部服务端档位，包含服务端标记为 `isVip` 的档位。
4. 选择另一档，确认播放位置和播放/暂停状态保持不变。
5. 确认界面没有 VIP 文案、锁定图标或会员跳转。
