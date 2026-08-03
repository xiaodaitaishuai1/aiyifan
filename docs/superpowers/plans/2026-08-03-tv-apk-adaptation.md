# TV 独立 APK 适配实施计划

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

Goal: 构建独立安装、横屏、可用遥控器操作的 Android TV APK，覆盖浏览、搜索、播放、本机收藏和历史、代理设置。

Architecture: 现有单一 app 模块增加 mobile 与 tv product flavor。核心数据、网络、Media3 和代理运行时保留在 src/main；TV 页面、布局和入口位于 src/tv。收藏和观看历史由可注入状态存储持久化，TV 的独立 application ID 使其与手机隔离。

Tech Stack: Kotlin、Android Views/View Binding、Gradle product flavors、RecyclerView、Media3、SharedPreferences、JUnit 4、Android Lint。

---

## Task 1: 创建 TV 构建变体和入口

Files:
- Modify: app/build.gradle.kts, app/src/main/AndroidManifest.xml
- Create: app/src/mobile/AndroidManifest.xml, app/src/tv/AndroidManifest.xml, app/src/tv/res/values/strings.xml
- Test: app/src/test/java/com/aiyifan/app/feature/tv/TvFlavorContractTest.kt

- [ ] Step 1: 编写失败的 TV Manifest 合同测试。

~~~kotlin
@Test
fun tvManifestHasLeanbackLauncherAndExcludesLogin() {
    val xml = File("app/src/tv/AndroidManifest.xml").readText()
    assertTrue(xml.contains("android.software.leanback"))
    assertTrue(xml.contains("android.intent.category.LEANBACK_LAUNCHER"))
    assertTrue(xml.contains(".feature.tv.TvMainActivity"))
    assertFalse(xml.contains("LoginActivity"))
}
~~~

- [ ] Step 2: 运行测试，确认 TV Manifest 尚不存在。

Run: ./gradlew.bat test --tests com.aiyifan.app.feature.tv.TvFlavorContractTest

Expected: NoSuchFileException 指向 app/src/tv/AndroidManifest.xml。

- [ ] Step 3: 加入 flavor 并拆分两个端的 Manifest。

~~~kotlin
flavorDimensions += "device"
productFlavors {
    create("mobile") { dimension = "device" }
    create("tv") {
        dimension = "device"
        applicationIdSuffix = ".tv"
        versionNameSuffix = "-tv"
    }
}
~~~

主 Manifest 只保留通用权限、AiyifanApp 和 ProxyForegroundService；把手机 Launcher、登录、画中画、悬浮窗和手机 Activity 迁到 src/mobile。TV Manifest 必须包含：

~~~xml
<uses-feature android:name="android.software.leanback" android:required="true" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<application android:label="@string/app_name">
    <activity android:name=".feature.tv.TvMainActivity"
        android:exported="true"
        android:screenOrientation="landscape">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent-filter>
    </activity>
</application>
~~~

覆盖 TV app_name 为“爱壹帆 TV”。

- [ ] Step 4: 验证并提交。

Run: ./gradlew.bat test --tests com.aiyifan.app.feature.tv.TvFlavorContractTest assembleTvDebug

Expected: 合同测试通过，生成 app/build/outputs/apk/tv/debug/app-tv-debug.apk。

Commit: feat: 增加电视独立构建变体。

## Task 2: 持久化本机收藏和观看历史

Files:
- Create: app/src/main/java/com/aiyifan/app/core/data/local/CatalogStateStore.kt, SharedPreferencesCatalogStateStore.kt
- Modify: app/src/main/java/com/aiyifan/app/core/data/FakeCatalogRepository.kt, app/src/main/java/com/aiyifan/app/core/data/AppGraph.kt
- Test: app/src/test/java/com/aiyifan/app/core/data/local/CatalogStateCodecTest.kt

- [ ] Step 1: 写入失败的 JSON 回退与覆盖写入测试。

~~~kotlin
@Test
fun invalidStoredJsonFallsBackToEmptyState() {
    assertEquals(emptyList<FavoriteVideo>(), CatalogStateCodec.decodeFavorites("not-json"))
    assertEquals(emptyList<WatchHistory>(), CatalogStateCodec.decodeHistory("not-json"))
}

@Test
fun historyReplacesMatchingMedia() {
    val store = InMemoryCatalogStateStore(clock = { 200L })
    store.saveHistory(history("a", 10L))
    store.saveHistory(history("a", 50L))
    assertEquals(50L, store.history().single().progressMs)
}
~~~

- [ ] Step 2: 运行测试，确认存储类型尚未定义。

Run: ./gradlew.bat test --tests com.aiyifan.app.core.data.local.CatalogStateCodecTest

Expected: 编译失败，未解析 CatalogStateCodec 与 InMemoryCatalogStateStore。

- [ ] Step 3: 实现可注入存储和生产接入。

~~~kotlin
interface CatalogStateStore {
    fun saveHistory(history: WatchHistory)
    fun history(): List<WatchHistory>
    fun clearHistory()
    fun toggleFavorite(detail: VideoDetail): Boolean
    fun isFavorite(mediaKey: String): Boolean
    fun favorites(): List<FavoriteVideo>
}

class FakeCatalogRepository(
    private val clock: () -> Long = System::currentTimeMillis,
    private val stateStore: CatalogStateStore = InMemoryCatalogStateStore(clock),
) : CatalogRepository {
    override fun getHistory() = stateStore.history()
    override fun clearHistory() = stateStore.clearHistory()
    override fun toggleFavorite(detail: VideoDetail) = stateStore.toggleFavorite(detail)
    override fun isFavorite(mediaKey: String) = stateStore.isFavorite(mediaKey)
    override fun getFavorites() = stateStore.favorites()
}
~~~

InMemoryCatalogStateStore 按 mediaKey 覆盖并按时间降序返回。编解码用 JSONArray 和 JSONObject 保存 FavoriteVideo 与 WatchHistory 全部字段；格式错误和缺字段回退为空列表。SharedPreferences 在 catalog_state 中读写 favorites、history JSON。AppGraph 把该存储传给 FakeCatalogRepository，再作为 RemoteCatalogRepository 的 fallback；无参 Fake 仓库继续给既有测试使用。

- [ ] Step 4: 验证并提交。

Run: ./gradlew.bat test --tests com.aiyifan.app.core.data.local.CatalogStateCodecTest --tests com.aiyifan.app.feature.video.VideoPlaybackControllerTest --tests com.aiyifan.app.core.data.remote.RemoteCatalogRepositoryPlaybackTest

Expected: 所有指定测试通过。

Commit: feat: 持久化本机收藏和观看历史。

## Task 3: 定义 TV 焦点和播放器按键策略

Files:
- Create: app/src/tv/java/com/aiyifan/app/feature/tv/TvNavigationPolicy.kt, TvPlayerKeyPolicy.kt
- Test: app/src/testTv/java/com/aiyifan/app/feature/tv/TvNavigationPolicyTest.kt, TvPlayerKeyPolicyTest.kt

- [ ] Step 1: 写入失败的策略测试。

~~~kotlin
@Test
fun leftFromFirstContentColumnTransfersFocusToRail() {
    assertEquals(TvFocusTarget.RAIL, TvNavigationPolicy.move(TvFocusTarget.CONTENT, TvDirection.LEFT, 0))
}

@Test
fun backHidesVisibleControlsBeforeNavigatingUp() {
    assertEquals(TvPlayerAction.HIDE_CONTROLS, TvPlayerKeyPolicy.action(TvKey.BACK, true))
}
~~~

- [ ] Step 2: 运行测试，确认策略类型未定义。

Run: ./gradlew.bat testTvDebugUnitTest --tests com.aiyifan.app.feature.tv.TvNavigationPolicyTest --tests com.aiyifan.app.feature.tv.TvPlayerKeyPolicyTest

Expected: 编译失败，策略类型未解析。

- [ ] Step 3: 实现纯 Kotlin 策略。

~~~kotlin
enum class TvFocusTarget { RAIL, CONTENT }
enum class TvDirection { LEFT, RIGHT, UP, DOWN }
enum class TvKey { CENTER, LEFT, RIGHT, UP, DOWN, BACK }
enum class TvPlayerAction { SHOW_CONTROLS, HIDE_CONTROLS, SEEK_BACK, SEEK_FORWARD, NAVIGATE_UP, NONE }

object TvNavigationPolicy {
    fun move(target: TvFocusTarget, direction: TvDirection, column: Int) =
        if (target == TvFocusTarget.CONTENT && direction == TvDirection.LEFT && column == 0) TvFocusTarget.RAIL else target
}

object TvPlayerKeyPolicy {
    fun action(key: TvKey, controlsVisible: Boolean) = when (key) {
        TvKey.CENTER -> if (controlsVisible) TvPlayerAction.HIDE_CONTROLS else TvPlayerAction.SHOW_CONTROLS
        TvKey.LEFT -> TvPlayerAction.SEEK_BACK
        TvKey.RIGHT -> TvPlayerAction.SEEK_FORWARD
        TvKey.BACK -> if (controlsVisible) TvPlayerAction.HIDE_CONTROLS else TvPlayerAction.NAVIGATE_UP
        TvKey.UP, TvKey.DOWN -> TvPlayerAction.NONE
    }
}
~~~

Activity 代码只把 KeyEvent 映射为 TvKey，不再自行编写按键分支。

- [ ] Step 4: 验证并提交。

Run: ./gradlew.bat testTvDebugUnitTest --tests com.aiyifan.app.feature.tv.TvNavigationPolicyTest --tests com.aiyifan.app.feature.tv.TvPlayerKeyPolicyTest

Expected: 覆盖焦点转移、控制层、快进、快退和返回的测试通过。

Commit: feat: 增加电视遥控器焦点策略。

## Task 4: 实现左侧导航与首页内容行

Files:
- Create: app/src/tv/java/com/aiyifan/app/feature/tv/TvMainActivity.kt, TvHomeFragment.kt, TvHomeRowAdapter.kt, TvVideoPosterAdapter.kt
- Create: app/src/tv/res/layout/activity_tv_main.xml, fragment_tv_home.xml, item_tv_home_row.xml, item_tv_video_poster.xml
- Create: app/src/tv/res/drawable/bg_tv_focus.xml, app/src/tv/res/values/dimens.xml
- Test: app/src/test/java/com/aiyifan/app/feature/tv/TvHomeLayoutTest.kt

- [ ] Step 1: 写入失败的首页布局合同。

~~~kotlin
@Test
fun tvMainLayoutHasPersistentRailAndContentContainer() {
    val xml = File("app/src/tv/res/layout/activity_tv_main.xml").readText()
    assertTrue(xml.contains("@+id/tvNavigationRail"))
    assertTrue(xml.contains("@+id/tvContentContainer"))
}
~~~

- [ ] Step 2: 运行测试，确认布局尚不存在。

Run: ./gradlew.bat test --tests com.aiyifan.app.feature.tv.TvHomeLayoutTest

Expected: NoSuchFileException。

- [ ] Step 3: 创建导航、内容行和焦点态。

TvMainActivity 保持左侧导航常驻，并在首页、搜索、收藏、历史、设置 Fragment 间替换内容。首页加载分类及各自 getHomeVideos(category.id) 结果；行使用横向 LinearLayoutManager 和 TvVideoPosterAdapter，点击海报打开 TvDetailActivity.intent(context, mediaKey)。

~~~xml
<LinearLayout android:id="@+id/tvPosterCard"
    android:layout_width="@dimen/tv_poster_width"
    android:layout_height="wrap_content"
    android:focusable="true"
    android:foreground="@drawable/bg_tv_focus"
    android:orientation="vertical" />
~~~

焦点将海报缩放为 1.06f/1f；第一列左移经 TvNavigationPolicy 回到选中导航项；每行显示六张并横向滚动。失败状态提供可聚焦“重试”按钮，且不移除导航栏。

- [ ] Step 4: 验证并提交。

Run: ./gradlew.bat test --tests com.aiyifan.app.feature.tv.TvHomeLayoutTest assembleTvDebug

Expected: 布局测试和 TV 编译通过。

Commit: feat: 增加电视首页和侧边导航。

## Task 5: 实现搜索、收藏、历史和设置

Files:
- Create: app/src/tv/java/com/aiyifan/app/feature/tv/TvSearchFragment.kt, TvLibraryFragment.kt, TvSettingsFragment.kt, TvSearchHistoryStore.kt
- Create: app/src/tv/res/layout/fragment_tv_search.xml, fragment_tv_library.xml, fragment_tv_settings.xml
- Test: app/src/testTv/java/com/aiyifan/app/feature/tv/TvSearchHistoryStoreTest.kt, TvSettingsLayoutTest.kt

- [ ] Step 1: 写入失败的搜索历史与无登录设置测试。

~~~kotlin
@Test
fun newSearchIsFirstAndDuplicatesAreRemoved() {
    val store = TvSearchHistoryStore(InMemoryStringStore())
    store.save("电影"); store.save("电视剧"); store.save("电影")
    assertEquals(listOf("电影", "电视剧"), store.read())
}

@Test
fun tvSettingsHasThemeAndProxyButNoLogin() {
    val xml = File("app/src/tv/res/layout/fragment_tv_settings.xml").readText()
    assertTrue(xml.contains("@+id/themeButton"))
    assertTrue(xml.contains("@+id/proxySettingsButton"))
    assertFalse(xml.contains("login"))
}
~~~

- [ ] Step 2: 运行测试，确认页面类型尚不存在。

Run: ./gradlew.bat testTvDebugUnitTest --tests com.aiyifan.app.feature.tv.TvSearchHistoryStoreTest --tests com.aiyifan.app.feature.tv.TvSettingsLayoutTest

Expected: 编译或文件读取失败。

- [ ] Step 3: 实现 TV 工具页面。

搜索历史以有序 JSON 数组保存最多 20 个非空词。搜索输入框获得焦点时调起系统软键盘，确认后调用 searchVideos，建议调用 searchSuggestions，结果和失败重试都复用 TvVideoPosterAdapter。TvLibraryFragment 接收 FAVORITES 或 HISTORY，从仓库读取栅格数据；清空历史先显示包含确认/取消焦点按钮的对话框。

设置页只包含主题和代理：

~~~kotlin
ThemePreferenceStore(requireContext()).select(ThemeMode.entries[checkedIndex])
~~~

代理按钮启动 ProxySettingsActivity，并在 TV 源集中覆盖为横屏、焦点背景和至少 48dp 的操作目标。不得创建或引用 LoginActivity。

- [ ] Step 4: 验证并提交。

Run: ./gradlew.bat testTvDebugUnitTest --tests com.aiyifan.app.feature.tv.TvSearchHistoryStoreTest --tests com.aiyifan.app.feature.tv.TvSettingsLayoutTest assembleTvDebug

Expected: 指定测试与 TV 编译通过。

Commit: feat: 增加电视搜索收藏和设置。

## Task 6: 实现 TV 详情与全屏播放器

Files:
- Create: app/src/tv/java/com/aiyifan/app/feature/tv/TvDetailActivity.kt, TvPlayerActivity.kt
- Create: app/src/tv/res/layout/activity_tv_detail.xml, activity_tv_player.xml
- Modify: app/src/tv/AndroidManifest.xml
- Test: app/src/test/java/com/aiyifan/app/feature/tv/TvPlayerLayoutTest.kt

- [ ] Step 1: 写入失败的 TV 播放器隔离测试。

~~~kotlin
@Test
fun tvPlayerIsFullScreenWithoutFloatingControls() {
    val xml = File("app/src/tv/res/layout/activity_tv_player.xml").readText()
    assertTrue(xml.contains("@+id/playerView"))
    assertFalse(xml.contains("floatingWindowButton"))
    assertFalse(xml.contains("inAppMiniPlayer"))
}
~~~

- [ ] Step 2: 运行测试，确认布局尚不存在。

Run: ./gradlew.bat test --tests com.aiyifan.app.feature.tv.TvPlayerLayoutTest

Expected: NoSuchFileException。

- [ ] Step 3: 接入共享播放会话。

详情页加载 VideoDetail，展示海报、简介、收藏和横向剧集；确认剧集启动 TvPlayerActivity.intent(this, mediaKey, episodeKey)。播放器解析媒体流后复用现有会话：

~~~kotlin
val controller = AppGraph.videoPlaybackController
controller.prepare(detail, episode, startPositionMs = historyProgress)
controller.attach(binding.playerView)
~~~

根 View 将 DPAD 映射到 TvPlayerKeyPolicy：左右按 10 秒 seek 并夹紧到 [0, duration]；确认显示/隐藏控制层；返回先关闭控制层，再 saveHistory()、detach()、finish()。onStop() 保存历史，onDestroy() 仅 detach。两个 Activity 均在 TV Manifest 中横屏声明，不加入画中画、悬浮窗、迷你播放器或触摸手势。

- [ ] Step 4: 验证并提交。

Run: ./gradlew.bat testTvDebugUnitTest --tests com.aiyifan.app.feature.tv.TvPlayerKeyPolicyTest --tests com.aiyifan.app.feature.tv.TvPlayerLayoutTest assembleTvDebug

Expected: 播放器测试和 TV 编译通过。

Commit: feat: 增加电视详情和全屏播放。

## Task 7: 全量回归与设备验收

Files:
- Modify: app/src/test/java/com/aiyifan/app/feature/tv/TvFlavorContractTest.kt, TvHomeLayoutTest.kt
- Modify: docs/superpowers/specs/2026-08-03-tv-apk-adaptation-design.md，仅在实现迫使设计改变时

- [ ] Step 1: 完善最终资源合同。

在 TvFlavorContractTest 断言 TV Manifest 不含 LoginActivity、FloatingPlayerService、SYSTEM_ALERT_WINDOW 和 supportsPictureInPicture。在 TvHomeLayoutTest 断言六列海报、每个导航项 focusable=true，以及各页面切换后 tvNavigationRail 仍存在。

- [ ] Step 2: 运行全量自动验证。

Run: ./gradlew.bat test assembleMobileDebug assembleTvDebug lint

Expected: 所有 JUnit 4 测试通过，手机 APK 与 app-tv-debug.apk 生成，Lint 退出码为 0。

- [ ] Step 3: 在 Android TV 或 Google TV 模拟器手工验收。

1. 安装 TV APK，确认桌面显示“爱壹帆 TV”且横屏启动。
2. 从首页首卡进入和离开导航栏，确认焦点环清晰可见。
3. 验证软键盘搜索、建议、结果、空结果和重试。
4. 收藏、播放、退出、重启，确认收藏与进度仍在；清空历史后重启仍为空。
5. 验证主题切换、代理连接/失败/重试，以及确认、左右、上下、返回键。
6. 确认没有登录入口、画中画、悬浮窗、迷你播放器和触摸手势入口。

- [ ] Step 4: 提交验收合同。

Commit: test: 补充电视适配验收覆盖。
