# 爱壹帆 HarmonyOS NEXT 客户端

这是与 Android 工程并行维护的 HarmonyOS NEXT 原生客户端骨架，使用 ArkTS + ArkUI。

## 开发环境

- DevEco Studio（对应项目目标 API 版本）
- HarmonyOS NEXT SDK
- Node.js 与 Hvigor（由 DevEco Studio 管理）

## 当前状态

当前仅提供主导航、临时主题切换、草拟领域模型及网络、存储、播放器接口。业务尚未实现；`ApiConfig.ets` 为无效占位地址，不能用于联网验收。Android 真实服务依赖 RemoteConfigResolver 动态解析，后续按实际代码移植。Android 登录当前为本地昵称保存，不存在已实现的服务端认证；收藏和历史也为本地数据。

## 导入

在 DevEco Studio 中打开 `harmonyos` 目录，等待 Hvigor 同步后运行 `entry` 模块。

## Windows 构建

已使用 DevEco Studio 26.0.0.865 自带 SDK 验证构建。最低兼容 API 12，目标 API 26。

```powershell
.\build.ps1
# 自定义安装目录
.\build.ps1 -DevEcoPath 'D:\DevEco Studio'
```

产物为 `entry/build/default/outputs/default/entry-default-unsigned.hap`，未签名包不代表已经通过真机运行验证。需在 DevEco Studio 配置本地调试签名并连接设备或启动模拟器后验证。签名文件与本地环境文件不入库。

## 当前已实现

- 首页目录：读取配置接口，调用 `api/List/Index`，解析分区和视频，过滤广告、去重；网络失败时页面显示截断后的具体错误，便于模拟器诊断。
- 首页/热门/我的导航：热门展示目录前三个分区，暂无登录和收藏页面。
- 目录搜索：在已加载目录中按标题和分区名称过滤，支持空结果和错误重试。
- 本地解析测试：`tests/home.test.cjs`。

## 模拟器测试

DevEco Studio 已配置 Mate 90 Pro、Mate X7、MateBook Pro 和 MatePad Pro 13 实例。首次使用需要在 DevEco Studio 的 Device Manager 中接受 HarmonyOS 软件和 SDK 协议，并安装对应 HarmonyOS 26.0.0 手机镜像。当前命令行检查未发现在线设备，因此尚未完成模拟器安装启动验证。

构建通过后，在 Device Manager 启动 Mate 90 Pro，再使用 DevEco Studio 的 Run；需要调试签名后才能安装 HAP。
