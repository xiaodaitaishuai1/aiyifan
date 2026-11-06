# HarmonyOS NEXT 原生迁移实施计划

> **For agentic workers:** Use the executing-plans workflow to implement this plan task-by-task.

**Goal:** 在现有 Android 工程之外建立可持续扩展的 HarmonyOS NEXT 原生客户端，并按阶段迁移完整观影体验。

**Architecture:** 鸿蒙端使用 ArkTS + ArkUI 与 Stage 模型，接口、领域模型、存储和播放器通过独立协议边界组织。Android 工程保持不变，鸿蒙端按页面、数据、播放和系统能力分阶段实现。

**Tech Stack:** HarmonyOS NEXT、ArkTS、ArkUI、Hvigor、Preferences/Data Storage、AVPlayer（平台适配层）。

---

## 阶段清单

1. 工程与导航：EntryAbility、主导航、主题和页面状态。
2. 网络与首页：模型、目录、分类、热门、图片、刷新和分页。
3. 搜索与详情：搜索历史、热搜、联想、详情、剧集、评论和相关推荐。
4. 播放器：HLS、选集、续播、全屏、质量、语言、字幕、错误恢复。
5. 账号与本地数据：登录、收藏、观看历史、主题和进程恢复。
6. 本地媒体与系统能力：本地视频、后台播放、小窗/画中画、悬浮播放、睡眠定时。
7. 视觉与发布：资源、空态、错误态、权限、签名、Release 构建和真机升级。

## 当前已落地

- HarmonyOS NEXT 工程入口和 ArkUI 主导航骨架。
- 领域模型：视频、剧集、播放质量、历史、收藏和会话。
- 网络 API 接口边界与 API 地址配置入口。
- 本地存储接口边界。
- 播放器控制器接口和状态模型。
- DevEco Studio 导入说明。

## 验证

- DevEco Studio 导入 `harmonyos` 目录并完成 Hvigor 同步。
- 真机或模拟器启动 `entry` 模块，确认主导航和主题切换。
- 后续每阶段增加对应 ArkTS 单元/组件测试，并使用同一组 Android 接口样例做回归。
