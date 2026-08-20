# AiNfcDemo

一个用于演示 AI 协作研发流程的 Android NFC Demo。

## 工程组成

- `app`：单页演示应用，默认使用模拟 NFC 输入，也可切换到真实 NFC Reader Mode。
- `nfc-reader`：可独立复用的 NFC Reader Android Library。

## 支持能力

- 标准 NDEF Text（UTF-8 / UTF-16）
- 标准 NDEF URI（含 NFC Forum URI 前缀压缩）
- 同一次读取返回 Message 中的 Text 与 URI 两种内容
- 读取成功时提供短振动反馈
- 通过标准 `NdefMessage / NdefRecord` 模拟 NFC 输入
- Android 真实 NFC 标签读取
- 无 NFC、NFC 未开启、非 NDEF、未知 Record 和异常 payload 状态处理

## 运行

使用 Android Studio 打开工程，或执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Debug APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`。模拟器启动 App 后默认即为演示模式，可分别模拟 Text、URL，或一次模拟 Text + URL。

Library 的完整接入方式见 [NFC Reader Module 接入说明](docs/NFC-Reader-Module接入说明.md)，现场协作流程见 [AI 协作研发演示说明](docs/AI协作研发演示说明.md)。
