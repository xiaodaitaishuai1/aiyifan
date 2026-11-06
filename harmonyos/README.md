# 爱壹帆 HarmonyOS NEXT 客户端

这是与 Android 工程并行维护的 HarmonyOS NEXT 原生客户端骨架，使用 ArkTS + ArkUI。

## 开发环境

- DevEco Studio（对应项目目标 API 版本）
- HarmonyOS NEXT SDK
- Node.js 与 Hvigor（由 DevEco Studio 管理）

## 当前状态

第一阶段提供主导航、主题切换、领域模型、网络接口边界、本地存储边界和播放器接口。真实接口地址通过 `entry/src/main/ets/common/network/ApiConfig.ets` 配置，播放器由平台适配层注入。

## 导入

在 DevEco Studio 中打开 `harmonyos` 目录，等待 Hvigor 同步后运行 `entry` 模块。
