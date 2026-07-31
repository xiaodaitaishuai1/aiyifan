# VPN 刷新与播放器标题栏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** VPN 连接成功时刷新当前首页或热门页，并在普通和全屏播放模式显示单行视频标题与返回操作。

**Architecture:** `ProxyManager` 在连接成功后向可注册观察者分发事件，页面按视图生命周期订阅。播放器将标题写入两个工具栏，全屏浮层的显示状态由现有 Media3 控制器可见性驱动。

**Tech Stack:** Kotlin、Android View Binding、Fragment lifecycle、Media3、JUnit 4。

---

### Task 1: 连接成功观察者

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/feature/proxy/ProxyManager.kt`
- Test: `app/src/test/java/com/aiyifan/app/feature/proxy/ProxyManagerTest.kt`

- [ ] 写失败测试：注册观察者后成功连接通知一次；移除后和连接失败时不通知。
- [ ] 运行 `./gradlew.bat :app:testDebugUnitTest --tests "com.aiyifan.app.feature.proxy.ProxyManagerTest"`，确认新 API 未定义而失败。
- [ ] 实现 `addConnectionObserver` 与 `removeConnectionObserver`，在 `connect()` 的运行时连接成功分支通知观察者。
- [ ] 重跑代理测试，确认通过。

### Task 2: 当前页刷新

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt`
- Modify: `app/src/main/java/com/aiyifan/app/feature/hot/HotFragment.kt`
- Test: `app/src/test/java/com/aiyifan/app/feature/home/HomeFragmentLifecycleSafetyTest.kt`
- Test: `app/src/test/java/com/aiyifan/app/feature/hot/HotFragmentRefreshContractTest.kt`

- [ ] 写失败契约测试：两页在创建视图时订阅，在销毁视图时移除；首页回调使用 `loadHome()`，热门页使用抽取出的 `loadHot()`。
- [ ] 实现回调，使用视图生命周期协程切回主线程调用加载函数；热门页的首次加载与回调刷新共用 `loadHot()`。
- [ ] 运行首页与热门页测试，确认通过。

### Task 3: 普通与全屏播放器标题栏

**Files:**
- Modify: `app/src/main/res/layout/activity_video_player.xml`
- Modify: `app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt`
- Test: `app/src/test/java/com/aiyifan/app/feature/video/VideoPlayerLayoutTest.kt`

- [ ] 写失败布局测试：普通顶部栏和全屏浮层均包含单行、末尾省略的标题；全屏浮层有返回按钮。
- [ ] 在普通顶部栏加入加权标题；在播放器容器内加入全屏标题浮层和返回按钮。详情加载成功时同步两处标题。
- [ ] 在控制器可见性回调中更新全屏标题栏，只有全屏且控制器显示时可见；全屏返回按钮调用 `setFullScreen(false)`，画中画时隐藏该浮层。
- [ ] 运行播放器布局测试，确认通过。

### Task 4: 验证

- [ ] 运行 `./gradlew.bat test`、`./gradlew.bat lint` 和 `./gradlew.bat assembleDebug`。
- [ ] 检查 `git diff --check`，确认不修改用户已有 ABI 配置。
