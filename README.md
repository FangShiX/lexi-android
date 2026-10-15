# Lexi Android

本地优先的 Android 单词学习与阅读应用。Android 8.0 及以上，无需账号。

- 统一词库：搜索、筛选、来源、词书计划与灵活内容模块，支持 `.lexipack` 导入导出。
- 学习与复习：日内重复、长期 SRS、六类按内容能力启用的练习、拼写判断和系统发音。
- 阅读：TXT、HTML、EPUB、无 DRM 经典 MOBI/AZW、PDF 与图片 OCR；点词收藏、书签、笔记、正文校正。
- 试卷：结构化题目导入、OCR 辅助整理、客观判分、主观自评、暂存与历史记录。
- 学习统计、每日提醒、日间/夜间模式、字号行高，以及包含素材的完整备份恢复。

## 开始使用

从 [Releases](https://github.com/FangShiX/lexi-android/releases) 下载 APK。首次打开，在“词库”点击“体验示例词库”，或自行添加/导入内容；右上“管理词书”可调整书内计划。

正式版本的应用 ID 为 `io.github.fangshix.lexi`，与旧 Alpha 并存。旧版用户请先导出完整备份，再在新应用“我的 → 备份与恢复”中恢复；确认数据完整后再处理旧版。不要先卸载旧版。

PDF 采用文本提取/OCR，不是原版面 PDF 编辑器；OCR 结果请校正。AZW3/KF8、HUFF/CDIC 和 DRM 不支持，可先转换成无 DRM EPUB。词义与素材的质量取决于导入内容，缺少可靠答案的题型不会启用。

## 构建

JDK 17、Android SDK 35。执行：

```sh
./gradlew testDebugUnitTest lintRelease assembleDebug assembleRelease bundleRelease
```

签名由环境变量 `LEXI_KEYSTORE_PATH`、`LEXI_KEYSTORE_PASSWORD`、`LEXI_KEY_ALIAS`、`LEXI_KEY_PASSWORD` 提供。未配置时 Release 不签名；密钥不属于源码。

`.lexipack` 是 ZIP，包含 `manifest.json` 和 UTF-8 `entries.jsonl`。清单版本为 1 或 2，`book` 或 `source` 包含 `name`、`entryCount`；每行至少提供 `id`、`word`，可以附释义、义项、例句、词形、模块及来源。内置 `app/src/main/assets/starter.lexipack` 提供自写示例。请只导入有权使用的资料。

## 隐私与许可

应用不申请联网权限，开发者不接收学习资料，也不启用系统自动备份。OCR 使用 ML Kit 在本机处理图像与识别结果；SDK 的设备、应用及性能诊断政策见 [官方条款](https://developers.google.com/ml-kit/terms)。系统 TTS 可能由第三方服务提供，其行为由系统设置控制。手动导出的备份未加密，请妥善保管；卸载将移除应用本地数据。

代码采用 [Apache-2.0](LICENSE)，第三方归属见 [NOTICE](NOTICE)。应用内“设置 → 数据”提供帮助、隐私与完整 UI 组件许可。

问题反馈：[Issues](https://github.com/FangShiX/lexi-android/issues)。请提供版本和复现步骤，勿上传私人资料或完整备份。
