# 全量硬编码资源化设计

## 目标

将应用中可资源化的硬编码值集中到 Android 资源文件中：

- XML 中的 `dp`、`sp` 改为 `@dimen`。
- XML 和 Kotlin 中除颜色定义外的十六进制颜色改为 `@color`。
- XML 与 Kotlin 的全部字符串字面量改为 `@string`。

本次范围包含界面文案、提示、默认展示文本、网络协议值、URL、JSON 键、SharedPreferences 文件名和键、Intent extra 键、请求头、正则表达式和格式模板。数值型业务规则（例如超时、分页大小、比例）不属于 `dimen`，保持 Kotlin 常量，避免将非界面语义错误地放入尺寸资源。

## 资源组织

`values/dimens.xml` 继续作为唯一尺寸表。保留工作区中现有未提交的尺寸资源，缺少的小数值按现有 `dp_`、`sp_` 命名规则补齐。布局、Drawable、样式和 Kotlin 不再直接写 `dp` 或 `sp`。

`values/colors.xml` 和 `values-night/colors.xml` 作为颜色定义源。Drawable、布局、样式中的颜色属性均引用 `@color/...`；颜色定义自身保留十六进制值。新增颜色采用语义化名称，不以颜色值命名。

`values/strings.xml` 收纳所有字符串。面向用户的字符串使用可翻译默认值；协议、存储和解析用途的字符串标记为 `translatable="false"`。需要拼接参数的显示文案使用格式化占位符，调用点通过 `getString(id, args)` 或资源访问器格式化，避免 Kotlin 插值保留显示文本。

## Kotlin 访问边界

拥有 `Context` 的 Activity、Fragment、Service、Adapter 和 View 直接通过 `getString`、`resources.getDimensionPixelSize`、`ContextCompat.getColor` 读取资源。

没有 Android 上下文的普通 Kotlin 类通过一个可注入的 `ResourceAccessor` 接口读取字符串、尺寸和颜色。生产实现包装 application context；测试提供内存实现。接口使用资源 ID 和可变参数，不暴露资源文件名，也不允许调用方保留硬编码回退值。该方式使数据层和策略类不需要静态依赖 `Application`，同时仍满足所有值从资源读取的要求。

## 迁移步骤

1. 建立资源访问器及其测试替身，完成初始化和依赖传递。
2. 汇总 XML 尺寸、颜色和显示文字，分别替换为 `@dimen`、`@color`、`@string` 引用。
3. 按功能包迁移 Kotlin 字符串、颜色和尺寸字面量；对格式化、协议和持久化值保持原有运行时行为。
4. 扫描 `src/main`，确认不存在可迁移的 Kotlin 字符串、颜色字面量或 XML `dp/sp`；允许资源定义文件本身的颜色和尺寸值。
5. 执行单元测试、Debug 构建和 Android Lint。

## 错误处理与兼容性

资源 ID 在编译期生成，缺失引用会由编译阻断。格式字符串使用显式位置参数，防止参数排序造成文本错误。协议字符串保持不可翻译且原样存储，避免语言切换改变网络、解析或持久化行为。资源访问器的测试替身对未配置 ID 直接失败，防止测试因空字符串掩盖遗漏。

## 验收条件

- Kotlin 源码中没有字符串字面量、颜色字面量和 `dp/sp` 尺寸字面量。
- XML 的非定义位置不含直接颜色值或 `dp/sp` 尺寸；显示文字均引用 `@string`。
- 正常构建、现有单元测试和 Lint 均通过。
- 用户可见文本、网络请求、解析字段、持久化键和格式化输出与迁移前一致。
