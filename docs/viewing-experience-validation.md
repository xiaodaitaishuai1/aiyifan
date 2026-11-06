# 用户观影体验增强：实施与验证

日期：2026-11-06。分支：codex/viewing-experience；基于 c1046f3。

## 已交付

- 收藏持久化与追更快照；收藏页全部/有更新筛选、15 分钟检查间隔、下拉强制刷新、失败重试。严格远程刷新拒绝损坏剧集结构，失败保留快照。取消收藏与重新收藏使用 generation 防护异步旧结果。
- 观看记录专用卡片、剧集标题、时间与进度条、长按多选、全选、单条/批量/全部删除确认。旧历史兼容。
- 首页前置横向继续观看（最近五部），独立 ConcatAdapter 不重置 Banner、不影响分页；隐藏持久保存，新观看重新出现。监听历史保存更新。
- 在线、全屏、小窗、悬浮窗定时暂停：关闭、15/30/60 分钟、本集结束。单调时钟、暂停缓冲继续计时、晚到播放源不自动播放、本集结束不跳片尾或连播下一集、释放后清除。
- 验证中修复首页延迟图片加载访问已销毁 Activity 的既有崩溃；补齐既有签名配置示例测试需要的无凭据模板及 ignore 规则。未修改现有 app-config.gradle 或签名凭据；ignore 不会使已有跟踪文件自动取消跟踪。

## 验证

- `gradlew.bat testDebugUnitTest`：233 项通过，0 失败。
- `gradlew.bat assembleDebug`：通过。
- `gradlew.bat lintDebug`：通过，0 errors、147 warnings（未进行无关全仓告警清理）。
- `gradlew.bat assembleDebugAndroidTest`：通过。
- Pixel 6 / Android API 36：ViewingExperienceDeviceTest 与 PlayerExperienceDeviceTest 共 9 项通过，包含浅/深色继续观看、历史、收藏、长按选择、首页延迟图片生命周期回归、全屏/应用内小窗/悬浮窗切换、普通连播回归和睡眠定时。
- 同一设备分两次 instrumentation 运行 personalDataSurvivesProcessRestart，seed 后 force-stop，再 verify；两阶段通过，核对 PID 变化及收藏、剧集快照、历史进度恢复。
- 追更断网、损坏响应、取消、强制刷新、间隔、删除重加竞态由确定性假传输单元测试验证；未等待真实服务发布新集，未做实际断开设备网络的测试。
- 15/30/60 分钟的到期逻辑使用注入时钟测试，未在真机上等待完整 60 分钟；真机验证了倒计时状态、选项共享及真实媒体本集结束暂停。
- 独立规格及代码复核完成；发现的快照覆盖、取消任务刷新动画、界面对比度问题已修正。

## 本地输出

APK：`app/build/outputs/apk/debug/app-debug.apk`。

截图（仅本地，不提交设备产物）：
- `artifacts/viewing-experience/viewing-qa/continue-watching-light.png`
- `artifacts/viewing-experience/viewing-qa/continue-watching-dark.png`
- `artifacts/viewing-experience/viewing-qa/watch-history-light.png`
- `artifacts/viewing-experience/viewing-qa/watch-history-dark.png`
- `artifacts/viewing-experience/viewing-qa/collection-light.png`
- `artifacts/viewing-experience/viewing-qa/collection-dark.png`
- `artifacts/viewing-experience/aiyifan-player-qa/sleep-timer-portrait.png`
- `artifacts/viewing-experience/aiyifan-player-qa/sleep-timer-fullscreen-dialog.png`
- `artifacts/viewing-experience/aiyifan-player-qa/sleep-timer-mini.png`
- `artifacts/viewing-experience/aiyifan-player-qa/sleep-timer-overlay-dialog.png`
- `artifacts/viewing-experience/aiyifan-player-qa/sleep-timer-overlay-ended.png`

不做后台追更通知、账号同步或本地播放器定时。追更正确性依赖服务提供稳定 episodeKey；未提供有效剧集结构时显示检查失败并保留本地数据。
