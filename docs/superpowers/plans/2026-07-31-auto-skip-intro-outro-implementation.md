# 片头片尾自动跳过实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 播放页复刻参考 APK 的默认开启片头片尾自动跳过功能。

**Architecture:** `Episode` 保存片头和片尾秒数，远端仓库解析字段；纯 Kotlin 策略决定初始位置和片尾切集，Activity 监听播放器进度并执行策略，偏好存储保持开关状态。

**Tech Stack:** Kotlin、Android View Binding、Media3 ExoPlayer、SharedPreferences、JUnit 4、Android Lint。

---

### Task 1: 自动跳过策略

**Files:**
- Create: `app/src/main/java/com/aiyifan/app/feature/video/AutoSkipPolicy.kt`
- Create: `app/src/test/java/com/aiyifan/app/feature/video/AutoSkipPolicyTest.kt`

- [ ] **Step 1: 写入失败测试**

```kotlin
assertEquals(12_345L, AutoSkipPolicy.initialPositionMs(12_345L, 90L, true))
assertEquals(90_000L, AutoSkipPolicy.initialPositionMs(0L, 90L, true))
assertTrue(AutoSkipPolicy.shouldAdvance(80_000L, 80L, true, true, false))
assertFalse(AutoSkipPolicy.shouldAdvance(80_000L, 80L, true, true, true))
```

- [ ] **Step 2: 确认失败**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.video.AutoSkipPolicyTest`

Expected: FAIL，`AutoSkipPolicy` 未定义。

- [ ] **Step 3: 实现策略**

```kotlin
object AutoSkipPolicy {
    fun initialPositionMs(resumeMs: Long, introSecond: Long?, enabled: Boolean): Long =
        resumeMs.takeIf { it > 0L } ?: introSecond?.takeIf { enabled && it > 0L }?.times(1_000L) ?: 0L
    fun shouldAdvance(positionMs: Long, outroSecond: Long?, enabled: Boolean, hasNext: Boolean, advanced: Boolean): Boolean =
        enabled && hasNext && !advanced && outroSecond?.takeIf { it > 0L }?.let { positionMs >= it * 1_000L } == true
}
```

- [ ] **Step 4: 确认通过并提交**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.video.AutoSkipPolicyTest`

Expected: PASS。

Commit: `git add app/src/main/java/com/aiyifan/app/feature/video/AutoSkipPolicy.kt app/src/test/java/com/aiyifan/app/feature/video/AutoSkipPolicyTest.kt; git commit -m "feat(app): 新增片头片尾跳过策略"`

### Task 2: 远端时间点

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/core/model/Models.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepository.kt`
- Modify: `app/src/test/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepositoryPlaybackTest.kt`

- [ ] **Step 1: 写入失败的解析断言**

```kotlin
val response = """{"data":{"list":[{"resolution":"720P","mediaUrl":"https://example.com/720.m3u8","opSecond":90,"epSecond":2640}]}}"""
assertEquals(90L, result.opSecond)
assertEquals(2_640L, result.epSecond)
```

- [ ] **Step 2: 确认失败**

Run: `./gradlew.bat test --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryPlaybackTest`

Expected: FAIL，`Episode` 无时间点字段。

- [ ] **Step 3: 扩展模型和解析**

```kotlin
data class Episode(/* existing fields */ val opSecond: Long? = null, val epSecond: Long? = null)
private fun JSONObject.optionalRemoteSecond(key: String): Long? = opt(key).toString().toLongOrNull()?.takeIf { it >= 0L }
```

在 `parseEpisodes` 和 `parsePlayableEpisode` 填入字段；播放地址响应的非空值覆盖详情的旧值。

- [ ] **Step 4: 确认通过并提交**

Run: `./gradlew.bat test --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryPlaybackTest`

Expected: PASS。

Commit: `git add app/src/main/java/com/aiyifan/app/core/model/Models.kt app/src/main/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepository.kt app/src/test/java/com/aiyifan/app/core/data/remote/RemoteCatalogRepositoryPlaybackTest.kt; git commit -m "feat(app): 解析剧集片头片尾时间"`

### Task 3: 播放器进度与切集

**Files:**
- Create: `app/src/main/java/com/aiyifan/app/feature/video/AutoSkipPreferenceStore.kt`
- Modify: `app/src/main/java/com/aiyifan/app/feature/video/VideoPlaybackController.kt`
- Modify: `app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt`
- Modify: `app/src/test/java/com/aiyifan/app/feature/video/VideoPlaybackControllerTest.kt`

- [ ] **Step 1: 写入失败的进度监听测试**

```kotlin
controller.addPositionListener { positions += it }
engine.currentPosition = 34_000L
engine.dispatchPosition()
assertEquals(listOf(34_000L), positions)
```

- [ ] **Step 2: 确认失败**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.video.VideoPlaybackControllerTest`

Expected: FAIL，控制器没有进度监听 API。

- [ ] **Step 3: 实现偏好和播放器联动**

```kotlin
class AutoSkipPreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("playback", Context.MODE_PRIVATE)
    fun isEnabled() = preferences.getBoolean("auto_skip_intro_outro", true)
    fun setEnabled(enabled: Boolean) = preferences.edit().putBoolean("auto_skip_intro_outro", enabled).apply()
}
```

为引擎与控制器添加每秒进度回调。Activity 以 `AutoSkipPolicy.initialPositionMs` 传入 `prepare`；策略命中片尾时选择 `detail.episodes` 的下一项，设置一次性标记并调用 `loadEpisodePlayback`。

- [ ] **Step 4: 确认通过并提交**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.video.VideoPlaybackControllerTest`

Expected: PASS。

Commit: `git add app/src/main/java/com/aiyifan/app/feature/video/AutoSkipPreferenceStore.kt app/src/main/java/com/aiyifan/app/feature/video/VideoPlaybackController.kt app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt app/src/test/java/com/aiyifan/app/feature/video/VideoPlaybackControllerTest.kt; git commit -m "feat(app): 接入片头片尾自动跳过"`

### Task 4: 开关界面和完整验证

**Files:**
- Modify: `app/src/main/res/layout/activity_video_player.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/aiyifan/app/feature/video/VideoPlayerLayoutTest.kt`

- [ ] **Step 1: 写入失败布局测试**

```kotlin
assertNotNull(viewWithId(videoPlayerLayout(), "autoSkipIntroOutroSwitch"))
```

- [ ] **Step 2: 确认失败**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.video.VideoPlayerLayoutTest`

Expected: FAIL，找不到开关 ID。

- [ ] **Step 3: 添加开关和文案**

增加 `autoSkipIntroOutroSwitch`、`auto_skip_intro_outro`、`auto_skip_intro`、`auto_skip_outro`。Activity 初始化开关并保存切换状态，自动跳过片头和片尾切集时显示短 Toast。

- [ ] **Step 4: 完整验证并提交**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.video --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryPlaybackTest`

Expected: PASS。

Run: `./gradlew.bat lint`

Expected: BUILD SUCCESSFUL，无本次资源和布局问题。

Commit: `git add app/src/main/res/layout/activity_video_player.xml app/src/main/res/values/strings.xml app/src/test/java/com/aiyifan/app/feature/video/VideoPlayerLayoutTest.kt; git commit -m "feat(app): 提供片头片尾跳过开关"`
