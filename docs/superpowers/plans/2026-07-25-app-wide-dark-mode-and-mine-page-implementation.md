# 全应用暗黑模式与“我的”页优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让应用跟随系统浅色／深色外观，并以分组入口重构“我的”页，同时保留全部现有业务跳转。

**Architecture:** 保持 `Theme.MaterialComponents.DayNight.NoActionBar` 为唯一主题入口。使用同名日间和夜间语义颜色驱动既有布局及 Drawable；`setupEdgeToEdge` 根据 `uiMode` 配置系统栏图标。`MineFragment` 只替换视图层次，并保留既有入口 ID 和监听器。

**Tech Stack:** Kotlin、AndroidX AppCompat/Core、Material Components、View Binding、JUnit 4、XML 资源限定目录。

---

## 文件结构

- 新建 `app/src/main/res/values-night/colors.xml`：夜间语义色板。
- 修改 `app/src/main/res/values/colors.xml`：补充描边、输入面、芯片和海报占位色。
- 修改 `app/src/main/res/values/styles.xml`、`app/src/main/java/com/aiyifan/app/core/ui/Insets.kt`：让边到边系统栏随主题调整图标明暗。
- 新建 `app/src/main/java/com/aiyifan/app/core/ui/SystemBarAppearance.kt`：纯主题判断函数。
- 修改 `bg_button_outline.xml`、`bg_chip.xml`、`bg_episode_preview.xml`、`bg_search.xml`、`bg_poster.xml`：引用语义色。
- 新建 `ic_history.xml`、`ic_favorite.xml`、`ic_network.xml`、`ic_chevron_right.xml`：本地 Material 风格矢量图标。
- 修改 `fragment_mine.xml`、`strings.xml`：标题、访客资料卡和两个功能分组。
- 新建 `ThemeResourceContractTest.kt`、`SystemBarAppearanceTest.kt`、`MineLayoutTest.kt`：资源、系统栏和页面结构回归测试。

### Task 1: 主题资源契约

**Files:**
- Create: `app/src/test/java/com/aiyifan/app/core/ui/ThemeResourceContractTest.kt`
- Modify: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values-night/colors.xml`

- [ ] **Step 1: 写入失败的颜色契约测试**

```kotlin
@Test
fun `day and night palettes define the same semantic colors`() {
    assertEquals(colorNames("values"), colorNames("values-night"))
    assertTrue(requiredNames.all(colorNames("values")::contains))
}

private fun colorNames(directory: String): Set<String> =
    DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(resourceFile(directory)).getElementsByTagName("color")
        .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element }
            .map { it.getAttribute("name") }.toSet() }

private fun resourceFile(directory: String): File = sequenceOf(
    File("src/main/res/$directory/colors.xml"),
    File("app/src/main/res/$directory/colors.xml"),
).first(File::isFile)

private val requiredNames = setOf(
    "primary", "page_bg", "surface", "text_primary", "text_secondary", "accent",
    "white", "black", "outline", "field_bg", "chip_bg", "poster_placeholder",
)
```

- [ ] **Step 2: 运行测试并确认缺少 `values-night/colors.xml`**

Run: `./gradlew.bat test --tests com.aiyifan.app.core.ui.ThemeResourceContractTest`

Expected: FAIL，找不到夜间 `colors.xml`。

- [ ] **Step 3: 实现两套同名色板**

在日间色板新增 `outline=#D6D9DE`、`field_bg=#F0F2F5`、`chip_bg=#EEF0F4`、`poster_placeholder=#D9DEE7`。新建夜间色板并定义全部同名资源：`primary=#1D2025`、`page_bg=#121417`、`surface=#1D2025`、`text_primary=#F2F4F7`、`text_secondary=#AAB2BD`、`accent=#FF8D3A`、`white=#FFFFFF`、`black=#000000`、`outline=#3A404A`、`field_bg=#292D34`、`chip_bg=#2E333C`、`poster_placeholder=#3A404A`。

- [ ] **Step 4: 重新运行资源契约测试**

Run: `./gradlew.bat test --tests com.aiyifan.app.core.ui.ThemeResourceContractTest`

Expected: PASS，日间与夜间均定义十二个资源。

- [ ] **Step 5: 提交主题色板**

```powershell
git add app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml app/src/test/java/com/aiyifan/app/core/ui/ThemeResourceContractTest.kt
git commit -m "feat(app): 增加系统深色主题色板"
```

### Task 2: 系统栏和 Drawable 主题适配

**Files:**
- Create: `app/src/test/java/com/aiyifan/app/core/ui/SystemBarAppearanceTest.kt`
- Create: `app/src/main/java/com/aiyifan/app/core/ui/SystemBarAppearance.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/ui/Insets.kt`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/drawable/bg_button_outline.xml`
- Modify: `app/src/main/res/drawable/bg_chip.xml`
- Modify: `app/src/main/res/drawable/bg_episode_preview.xml`
- Modify: `app/src/main/res/drawable/bg_search.xml`
- Modify: `app/src/main/res/drawable/bg_poster.xml`

- [ ] **Step 1: 写入系统栏亮度的失败测试**

```kotlin
class SystemBarAppearanceTest {
    @Test fun `night mode uses light system bar icons`() {
        assertFalse(usesLightSystemBarIcons(Configuration.UI_MODE_NIGHT_YES))
    }
    @Test fun `day mode uses dark system bar icons`() {
        assertTrue(usesLightSystemBarIcons(Configuration.UI_MODE_NIGHT_NO))
    }
}
```

- [ ] **Step 2: 运行测试确认函数尚不存在**

Run: `./gradlew.bat test --tests com.aiyifan.app.core.ui.SystemBarAppearanceTest`

Expected: FAIL，`usesLightSystemBarIcons` 未解析。

- [ ] **Step 3: 实现函数和资源替换**

```kotlin
fun usesLightSystemBarIcons(uiMode: Int): Boolean =
    uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
```

将 `setupEdgeToEdge` 的固定 `true` 改为使用 `resources.configuration.uiMode` 和该函数，同时设置状态栏、导航栏图标。给 `styles.xml` 补充 `android:windowLightNavigationBar`。五个 Drawable 依次使用 `@color/outline`、`@color/chip_bg`、`@color/field_bg`、`@color/field_bg`、`@color/poster_placeholder`；不改播放器专属的三个固定深色 Drawable。

- [ ] **Step 4: 运行测试和资源构建**

Run: `./gradlew.bat test --tests com.aiyifan.app.core.ui.SystemBarAppearanceTest`

Expected: PASS，日间为 `true`，夜间为 `false`。

Run: `./gradlew.bat assembleDebug`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交系统栏和 Drawable 改造**

```powershell
git add app/src/main/java/com/aiyifan/app/core/ui/SystemBarAppearance.kt app/src/main/java/com/aiyifan/app/core/ui/Insets.kt app/src/main/res/values/styles.xml app/src/main/res/drawable app/src/test/java/com/aiyifan/app/core/ui/SystemBarAppearanceTest.kt
git commit -m "feat(app): 适配深色主题表面与系统栏"
```

### Task 3: “我的”页信息架构

**Files:**
- Create: `app/src/test/java/com/aiyifan/app/feature/mine/MineLayoutTest.kt`
- Create: `app/src/main/res/drawable/ic_history.xml`
- Create: `app/src/main/res/drawable/ic_favorite.xml`
- Create: `app/src/main/res/drawable/ic_network.xml`
- Create: `app/src/main/res/drawable/ic_chevron_right.xml`
- Modify: `app/src/main/res/layout/fragment_mine.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 写入失败的结构测试**

```kotlin
@Test
fun `mine screen presents profile and grouped navigation`() {
    val root = parseLayout()
    assertEquals("我的", viewWithId(root, "mineTitle").textContent.trim())
    assertEquals("LinearLayout", viewWithId(root, "profileCard").tagName)
    assertEquals("LinearLayout", viewWithId(root, "contentGroup").tagName)
    assertEquals("LinearLayout", viewWithId(root, "settingsGroup").tagName)
    listOf("historyButton", "collectionButton", "proxySettingsButton").forEach { id ->
        assertEquals("LinearLayout", viewWithId(root, id).tagName)
    }
}
```

复用 `MainNavigationLayoutTest` 的 XML 解析和双路径 `layoutFile()` 辅助函数。

- [ ] **Step 2: 运行测试并确认缺少 `mineTitle`**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.mine.MineLayoutTest`

Expected: FAIL，页面尚未定义 `mineTitle`。

- [ ] **Step 3: 实现资料卡、分组行和图标**

在 `fragment_mine.xml` 顶部加入 `mineTitle`，资料区设为 `profileCard`，显示圆形“游”头像、“游客”、同步提示和已有 `loginButton`。创建“内容与数据”+`contentGroup` 和“应用设置”+`settingsGroup`；把既有三项跳转 ID 改为可点击 `LinearLayout` 行，含左图标、标题和右箭头。保留原 ID 使 `MineFragment` 无需修改。

所有可见文字和图标说明写入 `strings.xml`；四个 24dp VectorDrawable 使用 `@color/accent` 或 `@color/text_secondary`。资料卡及分组使用 `@drawable/bg_surface`，行分隔使用 1dp 的 `@color/outline` View，避免嵌套卡片。

- [ ] **Step 4: 运行页面和主导航回归测试**

Run: `./gradlew.bat test --tests com.aiyifan.app.feature.mine.MineLayoutTest --tests com.aiyifan.app.feature.main.MainNavigationLayoutTest`

Expected: PASS，资料卡、两个分组及三项入口存在，主导航仍固定三项。

- [ ] **Step 5: 提交“我的”页优化**

```powershell
git add app/src/main/res/layout/fragment_mine.xml app/src/main/res/drawable/ic_history.xml app/src/main/res/drawable/ic_favorite.xml app/src/main/res/drawable/ic_network.xml app/src/main/res/drawable/ic_chevron_right.xml app/src/main/res/values/strings.xml app/src/test/java/com/aiyifan/app/feature/mine/MineLayoutTest.kt
git commit -m "feat(app): 优化我的页信息层级"
```

### Task 4: 全量验证和视觉回归

**Files:**
- Verify: `app/src/main/res/values/colors.xml`
- Verify: `app/src/main/res/values-night/colors.xml`
- Verify: `app/src/main/res/layout/fragment_mine.xml`
- Verify: `app/src/main/java/com/aiyifan/app/core/ui/Insets.kt`

- [ ] **Step 1: 执行本地单元测试**

Run: `./gradlew.bat test`

Expected: `BUILD SUCCESSFUL`，没有失败测试。

- [ ] **Step 2: 执行 Android Lint**

Run: `./gradlew.bat lint`

Expected: `BUILD SUCCESSFUL`，不产生新的 error。

- [ ] **Step 3: 生成调试 APK**

Run: `./gradlew.bat assembleDebug`

Expected: `BUILD SUCCESSFUL`，APK 输出到 `app/build/outputs/apk/debug/`。

- [ ] **Step 4: 执行浅色／深色视觉检查**

以两种系统外观打开首页、热门、“我的”、搜索、登录、历史／收藏、网络代理和视频详情。确认背景、卡片、输入框、文字、底部导航和系统栏可读；确认播放器仍保持深色；确认“我的”页四个操作仍跳转到原页面。

- [ ] **Step 5: 提交验证结果**

```powershell
git add app/src/main app/src/test
git commit -m "test(app): 验证全局深色主题"
```
