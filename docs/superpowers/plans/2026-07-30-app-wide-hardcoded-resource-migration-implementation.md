# 全量硬编码资源化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Kotlin 字符串、可资源化颜色和 UI 尺寸迁入 Android 资源，且保持行为不变。

**Architecture:** XML 定义集中在 `values`；持有 `Context` 的组件直接读取资源，普通 Kotlin 类注入 `ResourceAccessor`。协议、持久化和解析字符串均为 `translatable="false"`。

**Tech Stack:** Kotlin、Android Views、Android Resources、JUnit 4、Gradle。

---

### Task 1: 资源访问器

**Files:**
- Create: `app/src/main/java/com/aiyifan/app/core/ui/ResourceAccessor.kt`
- Create: `app/src/test/java/com/aiyifan/app/core/ui/ResourceAccessorTest.kt`
- Modify: `app/src/main/java/com/aiyifan/app/AiyifanApp.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test fun `fake accessor formats arguments`() {
    assertEquals("item 3", FakeResourceAccessor(mapOf(1 to "item %1$d")).string(1, 3))
}
```

- [ ] **Step 2: 验证测试失败**

Run: `./gradlew.bat testDebugUnitTest --tests com.aiyifan.app.core.ui.ResourceAccessorTest`
Expected: FAIL，测试替身不存在。

- [ ] **Step 3: 实现最小接口**

```kotlin
interface ResourceAccessor {
    fun string(@StringRes id: Int, vararg args: Any): String
    fun dimensionPixelSize(@DimenRes id: Int): Int
    @ColorInt fun color(@ColorRes id: Int): Int
}
```

生产实现包装 application context 的 `Resources` 与 `ContextCompat`；测试替身以 map 格式化值。`AiyifanApp` 创建单一生产实现。

- [ ] **Step 4: 验证与提交**

Run: `./gradlew.bat testDebugUnitTest --tests com.aiyifan.app.core.ui.ResourceAccessorTest`
Expected: PASS。

Commit: `git add app/src/main/java/com/aiyifan/app/core/ui/ResourceAccessor.kt app/src/main/java/com/aiyifan/app/AiyifanApp.kt app/src/test/java/com/aiyifan/app/core/ui/ResourceAccessorTest.kt && git commit -m "feat(app): 新增资源访问器"`

### Task 2: XML 资源化

**Files:**
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-night/colors.xml`
- Modify: `app/src/main/res/layout/*.xml`
- Modify: `app/src/main/res/drawable/*.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 扫描候选值**

Run: `rg -n --glob '*.xml' '[0-9]+(\\.[0-9]+)?(dp|sp)|#[0-9A-Fa-f]{3,8}' app/src/main/res`
Expected: 布局、Drawable 与样式候选值；资源定义文件本身除外。

- [ ] **Step 2: 替换 XML 引用**

所有非定义位置的 `dp/sp` 改为 `@dimen/dp_*`、`@dimen/sp_*`，颜色改为语义化 `@color/*`，`text`、`hint`、`contentDescription` 和 manifest `label` 改为 `@string/*`。`dimens.xml` 与颜色定义文件保留值定义。

- [ ] **Step 3: 验证与提交**

Run: `./gradlew.bat processDebugResources`
Expected: PASS，无资源引用错误。

Commit: `git add app/src/main/res app/src/main/AndroidManifest.xml && git commit -m "refactor(app): 资源化 XML 界面值"`

### Task 3: Kotlin UI 调用点

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/feature/**/*.kt`
- Modify: `app/src/main/java/com/aiyifan/app/core/ui/*.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 写格式化字符串失败测试**

```kotlin
@Test fun `accessor preserves formatted episode text`() {
    assertEquals("示例视频 第2集", FakeResourceAccessor(mapOf(2 to "%1$s 第%2$d集")).string(2, "示例视频", 2))
}
```

- [ ] **Step 2: 验证失败**

Run: `./gradlew.bat testDebugUnitTest --tests com.aiyifan.app.core.ui.ResourceAccessorTest`
Expected: FAIL，格式化测试未支持。

- [ ] **Step 3: 迁移调用点**

Activity、Fragment、Service、View、Adapter 以 `getString`/`context.getString` 获取文本；无 Context 类注入 `ResourceAccessor`。尺寸使用 `getDimensionPixelSize(R.dimen.*)`，颜色使用 `ContextCompat.getColor` 或访问器。格式文本使用位置参数资源。

- [ ] **Step 4: 验证与提交**

Run: `./gradlew.bat testDebugUnitTest --tests com.aiyifan.app.core.ui.ResourceAccessorTest`
Expected: PASS。

Commit: `git add app/src/main/java/com/aiyifan/app/feature app/src/main/java/com/aiyifan/app/core/ui app/src/main/res/values/strings.xml && git commit -m "refactor(app): 资源化 Kotlin 界面值"`

### Task 4: 数据与协议字符串

**Files:**
- Modify: `app/src/main/java/com/aiyifan/app/core/data/**/*.kt`
- Modify: `app/src/main/java/com/aiyifan/app/feature/proxy/**/*.kt`
- Modify: `app/src/test/java/com/aiyifan/app/**/*.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 写构造注入失败测试**

```kotlin
@Test fun `resolver obtains endpoint from accessor`() = runTest {
    val resolver = RemoteConfigResolver(fetcher, FakeResourceAccessor(mapOf(R.string.config_default_url to "https://dataexbbff.github.io/rawApp.json")))
    assertEquals("https://api.tripdata.app/", resolver.resolveBaseUrl())
}
```

- [ ] **Step 2: 验证失败**

Run: `./gradlew.bat testDebugUnitTest --tests com.aiyifan.app.core.data.remote.RemoteConfigResolverTest`
Expected: FAIL，构造函数未接收访问器。

- [ ] **Step 3: 迁移运行时字面量**

将 URL、HTTP 方法与头、JSON 字段、正则、Intent extra 键及 SharedPreferences 文件名和键迁至 `strings.xml`，均设置 `translatable="false"`。调用链和测试显式传入访问器，保持值与回退逻辑。

- [ ] **Step 4: 验证与提交**

Run: `./gradlew.bat testDebugUnitTest --tests com.aiyifan.app.core.data --tests com.aiyifan.app.feature.proxy`
Expected: PASS。

Commit: `git add app/src/main/java/com/aiyifan/app/core/data app/src/main/java/com/aiyifan/app/feature/proxy app/src/main/res/values/strings.xml app/src/test && git commit -m "refactor(app): 资源化数据层字符串"`

### Task 5: 扫描和全量验证

**Files:**
- Modify: 仅修复扫描发现的遗漏文件。

- [ ] **Step 1: Kotlin 扫描**

Run: `rg -n --glob '*.kt' '"[^"\\n]+"|#[0-9A-Fa-f]{3,8}|\\.(dp|sp)\\(' app/src/main/java`
Expected: 无可迁移字面量，包名和 import 不计入结果。

- [ ] **Step 2: XML 扫描**

Run: `rg -n --glob '*.xml' '[0-9]+(\\.[0-9]+)?(dp|sp)|#[0-9A-Fa-f]{3,8}' app/src/main/res`
Expected: 仅尺寸与颜色定义文件保留直接值。

- [ ] **Step 3: 完整验证并提交**

Run: `./gradlew.bat test assembleDebug lint`
Expected: PASS；首次构建本地 `libbox.aar` 可能较慢。

Commit: `git add app/src/main && git commit -m "refactor(app): 完成硬编码资源化"`
