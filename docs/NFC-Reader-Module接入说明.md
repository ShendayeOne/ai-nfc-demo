# NFC Reader Module 接入说明

- 文档日期：2026-08-20
- Module：`:nfc-reader`
- Module 版本/当前工程状态：Demo 基线版 1.0，源码可编译、单元测试已覆盖核心解析边界
- 包名：`com.example.nfcreader`

## 目录

1. 功能介绍
2. 支持能力
3. 当前不支持能力
4. 环境要求
5. Module 引入方式
6. Gradle 配置
7. Manifest / NFC 权限说明
8. 初始化/创建方式
9. 开始 NFC 读取
10. 停止 NFC 读取
11. 获取 Text
12. 获取 URL
13. 结果模型说明
14. 生命周期接入示例
15. 完整最小调用示例
16. 模拟数据能力
17. 常见异常状态
18. 回调线程与生命周期约束
19. 测试和验证结果
20. 当前能力边界

## 1. 功能介绍

`nfc-reader` 是一个不依赖 `app` 的 Android Library。它使用 Android `NfcAdapter` Reader Mode 发现标签，通过 `Ndef` 读取标准 NDEF Message，并把受支持的 Text 或 URI Record 转换为简单的 `NfcReadResult`。

Library 不弹 Toast、不持久化数据、不发网络请求。用户文案和业务行为由接入 App 决定。

## 2. 支持能力

- 检查设备是否支持 NFC、NFC 是否已开启。
- 读取 Android 可通过 `Ndef` 技术访问的标准 NDEF 标签。
- 解析 NFC Forum Well Known Type：RTD_TEXT。
- 解析 NFC Forum Well Known Type：RTD_URI。
- Text 支持 UTF-8、UTF-16、语言码以及空文本。
- URI 支持 NFC Forum URI Identifier Code 前缀压缩。
- 多 Record Message 中同时返回第一条 Text 与第一条 URI，不遗漏两种标准内容。
- 将非 NDEF、空消息、不支持类型、异常 payload 和 I/O 失败映射为稳定错误枚举。
- 使用标准 `NdefRecord` 构造模拟 Text / URI，并复用真实读取的同一解析链路。

## 3. 当前不支持能力

- NFC 写入或格式化。
- MIFARE、NfcV 块读写、ISO-DEP / APDU、自定义厂商协议。
- HCE、UID 业务转换、标签认证或加密。
- MIME、External Type、Smart Poster 等其他 NDEF Record。
- 任意多 Record 业务编排（当前只提取 Text 与 URI 各第一条）、数据库、网络校验、自动重试。

## 4. 环境要求

| 项目 | 当前要求 |
| --- | --- |
| Android minSdk | 24 |
| compileSdk | 37（接入工程可按实际兼容版本调整） |
| Java | 11 |
| Android 设备 | 真实读取需要 NFC 硬件；模拟能力不需要 |

接入工程不要求 Kotlin，也不依赖第三方 NFC SDK。

## 5. Module 引入方式

将完整的 `nfc-reader` 目录复制到目标工程根目录，并在目标工程 `settings.gradle` 中加入：

```groovy
include ':nfc-reader'
```

Module 只依赖 Android 平台 API 和测试期 JUnit，可独立从本 Demo 拿走。

## 6. Gradle 配置

目标 App 的 `build.gradle`：

```groovy
dependencies {
    implementation project(':nfc-reader')
}
```

如果目标工程使用版本目录，需要确保根工程能解析 Android Library Plugin。当前 Demo 的根配置为：

```toml
[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
```

## 7. Manifest / NFC 权限说明

Library 自带以下 Manifest 声明，依赖后会合并到 App：

```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature
    android:name="android.hardware.nfc"
    android:required="false" />
```

`NFC` 是普通权限，不需要运行时申请。`required="false"` 允许 App 安装到没有 NFC 的设备或模拟器，并由页面正常展示“不支持 NFC”。

## 8. 初始化/创建方式

在需要读取的 Activity 中创建一个实例：

```java
private final NfcReader nfcReader = new NfcReader();
```

实例仅在 `start()` 到 `stop()` 之间持有 Activity 与回调引用；`stop()` 会释放它们。

## 9. 开始 NFC 读取

在主线程、已恢复的 Activity 上调用：

```java
NfcAvailability availability = nfcReader.start(this, new NfcReaderCallback() {
    @Override
    public void onResult(NfcReadResult result) {
        // 回调位于主线程，可以直接更新 UI
    }

    @Override
    public void onError(NfcReadError error) {
        // 由 App 将错误枚举映射为用户文案
    }
});
```

返回值可能是：

- `AVAILABLE`：Reader Mode 已启用，可以靠近标签。
- `DISABLED`：设备支持 NFC，但系统 NFC 当前关闭。
- `UNSUPPORTED`：设备没有 NFC Adapter。

`DISABLED` 和 `UNSUPPORTED` 时不会启用 Reader Mode。

## 10. 停止 NFC 读取

```java
nfcReader.stop(this);
```

必须在主线程调用。建议放在 `Activity.onPause()`，以免后台页面继续接收标签或泄漏页面引用。重复停止是安全的。

## 11. 获取 Text

```java
if (result.getType() == NfcReadResult.Type.TEXT) {
    String text = result.getTextContent();
}
```

如果 Message 同时含有 Text 与 URI，类型为 `TEXT_AND_URL`。更通用的判断方式是：

```java
if (result.hasText()) {
    String text = result.getTextContent();
}
```

Text 的语言码只用于正确确定 payload 中文本起始位置，当前简化结果不对外返回语言码。空 Text 是合法结果，`getTextContent()` 返回 `""`。

## 12. 获取 URL

```java
if (result.getType() == NfcReadResult.Type.URL) {
    String uri = result.getUrlContent();
}
```

同时包含两种内容时建议使用：

```java
if (result.hasUrl()) {
    String uri = result.getUrlContent();
}
```

枚举名使用 `URL` 以便 Demo 展示，但解析对象是标准 RTD_URI，因此 `mailto:`、`tel:` 等标准 URI Scheme 也可作为内容返回。本 Module 不打开链接，也不校验网络可达性。

## 13. 结果模型说明

`NfcReadResult` 是不可变对象：

| API | 含义 |
| --- | --- |
| `getType()` | `TEXT`、`URL` 或 `TEXT_AND_URL` |
| `hasText()` | Message 是否包含 Text；空 Text 也返回 true |
| `getTextContent()` | 解码后的 Text；不存在时为 null |
| `hasUrl()` | Message 是否包含 URI |
| `getUrlContent()` | 完整 URI；不存在时为 null |

错误不混入成功模型。读取失败通过 `NfcReaderCallback.onError(NfcReadError)` 返回。

## 14. 生命周期接入示例

```java
@Override
protected void onResume() {
    super.onResume();
    NfcAvailability state = nfcReader.start(this, readerCallback);
    renderAvailability(state);
}

@Override
protected void onPause() {
    nfcReader.stop(this);
    super.onPause();
}
```

如果页面有“演示/真实”切换，只在真实模式且 Activity 已恢复时调用 `start()`；切回演示模式时立即调用 `stop()`。

## 15. 完整最小调用示例

```java
public final class NfcActivity extends AppCompatActivity {
    private final NfcReader nfcReader = new NfcReader();

    private final NfcReaderCallback callback = new NfcReaderCallback() {
        @Override
        public void onResult(NfcReadResult result) {
            if (result.hasText()) {
                showText(result.getTextContent());
            }
            if (result.hasUrl()) {
                showUrl(result.getUrlContent());
            }
        }

        @Override
        public void onError(NfcReadError error) {
            showError(error);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        NfcAvailability availability = nfcReader.start(this, callback);
        if (availability == NfcAvailability.DISABLED) {
            showNfcDisabled();
        } else if (availability == NfcAvailability.UNSUPPORTED) {
            showNfcUnsupported();
        }
    }

    @Override
    protected void onPause() {
        nfcReader.stop(this);
        super.onPause();
    }
}
```

## 16. 模拟数据能力

模拟能力用于模拟器和自动演示。它不是直接创建 `NfcReadResult`，而是：

```text
标准 NdefRecord -> NdefMessage -> NdefParser -> NfcReadResult
```

调用方式：

```java
NfcReadResult text = NfcSimulator.simulateText("zh", "欢迎使用 NFC Reader");
NfcReadResult url = NfcSimulator.simulateUri("https://www.example.com/nfc/demo");
NfcReadResult both = NfcSimulator.simulateTextAndUri(
        "zh",
        "欢迎使用 NFC Reader",
        "https://www.example.com/nfc/demo"
);
```

这两个方法同步返回结果，应在 Android 运行环境中调用。参数不允许为 null；构造出的异常模拟消息会抛出 `IllegalArgumentException`。

## 17. 常见异常状态

| 状态 | 出现场景 | 建议处理 |
| --- | --- | --- |
| `NfcAvailability.UNSUPPORTED` | 无 NFC 硬件/模拟器 | 展示“当前设备不支持 NFC” |
| `NfcAvailability.DISABLED` | NFC 开关关闭 | 展示“NFC 未开启” |
| `NfcReadError.NOT_NDEF` | 标签不支持 NDEF | 告知仅支持标准 NDEF |
| `EMPTY_MESSAGE` | NDEF Message 为空 | 提示没有可读取内容 |
| `UNSUPPORTED_RECORD` | 没有任何 Text/URI Record | 提示当前仅支持两种类型 |
| `MALFORMED_DATA` | payload 长度、前缀或编码异常 | 提示标签数据格式异常 |
| `READ_FAILED` | I/O、标签移开、安全或状态异常 | 提示重新靠近读取 |

## 18. 回调线程与生命周期约束

- `start()` 和 `stop()` 必须从主线程调用，否则抛出 `IllegalStateException`。
- Reader Mode 的标签发现和 NFC I/O 由 Android 后台回调线程执行。
- `onResult()` 和 `onError()` 始终切回主线程，可直接更新 View。
- `stop()` 后，已经排队但尚未投递的旧回调会被丢弃。
- 同一个 `NfcReader` 再次 `start()` 时会先关闭上一次 Reader Mode。
- Library 不创建长期线程，不保存 Context 单例。

## 19. 测试和验证结果

核心解析器是纯 Java，可使用普通 JVM JUnit 测试，不需要 Robolectric。当前覆盖：

- 英文、中文、空 Text、UTF-16 Text。
- Text 空 payload、语言码越界、非法 UTF-8。
- `https://www.`、`http://`、无压缩 `https://`、`mailto:` URI 前缀。
- URI 空 payload、未知前缀、非法 UTF-8。
- 不支持 TNF 与不支持 Well Known Type。
- Text-only、URL-only、Text + URL 结果模型及空结果保护。

标准验证命令：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

真实 NFC I/O 仍应在目标 Android 设备和目标标签上做一次实机验证；普通模拟器只能验证模拟解析与不支持 NFC 的页面状态。

## 20. 当前能力边界

本 Module 是面向演示和基础接入的标准 NDEF Reader，不宣称覆盖所有 NFC 标签。只有 Android 能以 `Ndef` 技术访问、且包含 RTD_TEXT 或 RTD_URI 的标签才会返回成功结果；同一 Message 同时包含两者时会一次返回两项。业务 URL 校验、页面跳转、重复标签去重、声音/振动、数据保存和网络请求都属于调用 App 的职责。
