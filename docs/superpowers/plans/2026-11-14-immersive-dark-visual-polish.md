# 沉浸深色视觉优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将现有 Android 页面统一为具有清晰层级和舒适对比度的沉浸深色影视界面，并通过构建、Lint、单元测试和模拟器截图验证。

**Architecture:** 以现有资源引用为边界，优先调整 `values-night` 颜色和公共 drawable，再收敛主要页面布局中的硬编码颜色、圆角和间距。保持 Kotlin 页面逻辑、导航、数据加载和现有浅色主题不变。

**Tech Stack:** Android XML、Material Components、Kotlin、Gradle Wrapper、ADB。

---

### Task 1: 统一深色主题令牌

**Files:**
- Modify: `app/src/main/res/values-night/colors.xml`
- Modify: `app/src/main/res/values-night/colors_navigation.xml`
- Modify: `app/src/main/res/values-night/styles.xml`
- Modify: `app/src/main/res/drawable/bg_surface.xml`
- Modify: `app/src/main/res/drawable/bg_button_primary.xml`
- Modify: `app/src/main/res/drawable/bg_button_outline.xml`
- Modify: `app/src/main/res/drawable/bg_chip_selected.xml`

- [ ] 将页面、表面、输入框、描边、主文字、次文字和强调色替换为设计规格中的深色令牌。
- [ ] 统一深色导航选中态为品牌橙色，并为公共表面增加 12dp 圆角与 1dp 低对比描边。
- [ ] 保持 `values/colors.xml` 浅色资源不变，确保主题切换仍使用资源覆盖。
- [ ] 运行 `./gradlew.bat :app:processDebugResources`，确认资源合并成功。

### Task 2: 收敛首页、短剧和分类标签层级

**Files:**
- Modify: `app/src/main/res/layout/fragment_home.xml`
- Modify: `app/src/main/res/layout/fragment_short_drama.xml`
- Modify: `app/src/main/res/layout/item_home_video.xml`
- Modify: `app/src/main/res/layout/item_home_banner.xml`
- Modify: `app/src/main/res/layout/item_navigation_category.xml`

- [ ] 将顶部栏、分类栏和内容列表统一使用深色表面，保留现有点击 ID 与 RecyclerView 结构。
- [ ] 将首页列表左右边距和卡片间距调整为 12–16dp，视频标题和元信息使用主/次文字令牌。
- [ ] 将短剧选中分类改为橙色软底，底部状态栏与页面内容保持间距。
- [ ] 运行 `./gradlew.bat :app:lint`，检查资源、可访问性和布局警告。

### Task 3: 优化我的页和列表卡片

**Files:**
- Modify: `app/src/main/res/layout/fragment_mine.xml`
- Modify: `app/src/main/res/layout/item_video_card.xml`
- Modify: `app/src/main/res/layout/item_local_video.xml`
- Modify: `app/src/main/res/layout/item_collection.xml`
- Modify: `app/src/main/res/layout/item_watch_history.xml`
- Modify: `app/src/main/res/layout/activity_simple_list.xml`

- [ ] 为我的页头图、统计卡片、功能分组和设置分组统一表面层级与 12dp 圆角。
- [ ] 调整功能行的图标背景、标题/副标题对比度和行间分割线，保持至少 44dp 高度。
- [ ] 将收藏、历史、本地媒体及通用视频卡片的背景、按钮和海报占位色统一到深色令牌。
- [ ] 验证空状态图片、空状态文字和删除/播放操作在深色主题下可读。

### Task 4: 统一搜索、热门、本地媒体和播放器

**Files:**
- Modify: `app/src/main/res/layout/activity_search.xml`
- Modify: `app/src/main/res/layout/fragment_hot.xml`
- Modify: `app/src/main/res/layout/activity_collection.xml`
- Modify: `app/src/main/res/layout/fragment_local_media.xml`
- Modify: `app/src/main/res/layout/activity_video_player.xml`
- Modify: `app/src/main/res/layout/view_video_controls.xml`

- [ ] 顶部栏、搜索框、筛选项、TabLayout 和空状态统一使用深色表面与橙色选中态。
- [ ] 播放器页面将视频区域保持纯黑，内容区使用深色表面，控制按钮和进度条只使用橙色强调。
- [ ] 保持现有播放器控制 ID、画中画、全屏和自动跳过开关行为不变。
- [ ] 运行 `./gradlew.bat :app:test` 和 `./gradlew.bat :app:assembleDebug`。

### Task 5: 模拟器视觉验收

**Files:**
- No source changes.

- [ ] 安装 Debug APK 到 `emulator-5554`，打开首页、我的页、短剧页、搜索页和播放器。
- [ ] 截取至少 390×844 视口截图，检查状态栏、底部导航、卡片边界、文字对比度和点击区域。
- [ ] 在第二台设备 `1C281FDF600BE2` 上至少检查首页和播放器，确认不同屏幕尺寸没有裁切。
- [ ] 汇总截图和命令输出，若出现布局溢出或对比度问题，先修复再重复验证。

### Task 6: 变更审查与提交

- [ ] `git diff --check` 无空白错误，确认只包含视觉资源、布局和设计文档变更。
- [ ] 重新运行 `./gradlew.bat test`、`./gradlew.bat lint` 和 `./gradlew.bat assembleDebug`，记录退出码。
- [ ] 使用中文 Conventional Commit 提交，例如 `feat(app): 优化沉浸深色页面视觉`。
