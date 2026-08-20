# AI1 NFC Demo 代码审核报告

- 审核角色：AI1（独立 Review）
- 审核日期：2026-08-20
- 审核对象：分支 `demo/ai-review`，HEAD `65df9b7 修正NFC组合结果展示`
- 审核方式：静态代码核对 + Git 历史比对 + 单元测试证据；本轮只审核，未修改任何业务代码

## 审核结论

**通过。**

NFC 读取结果展示链路（nfc-reader → NfcReadResult → App Callback → MainActivity → UI）三个场景核对无误，**当前代码中 Text + URI 组合场景可以完整同时展示**。历史上存在的组合展示缺陷已在提交 `65df9b7` 中以最小改动正确修复。

## 审核范围

本轮专项范围为结果展示链路，逐项核对三个场景：

| 场景 | 输入 | 核对点 |
| --- | --- | --- |
| 场景一 | Text != null，URI == null | 页面正确展示 Text，URL 区显示占位符 |
| 场景二 | Text == null，URI != null | 页面正确展示 URI，Text 区显示占位符 |
| 场景三（重点） | Text != null，URI != null | Text 与 URI **同时完整展示** |

附带检查（不扩大范围）：null/空结果处理、明显崩溃风险、生命周期、用户可见文案、本轮相关冗余逻辑。

涉及文件：

- `app/src/main/java/com/example/ainfcdemo/MainActivity.java`（展示层核心）
- `nfc-reader/src/main/java/com/example/nfcreader/NfcReadResult.java`（结果模型）
- `nfc-reader/src/main/java/com/example/nfcreader/NfcSimulator.java`（模拟链路）
- `app/src/main/res/layout/activity_main.xml` 及结果项布局
- Git 提交 `65df9b7` 的 Diff

## 三场景核对结果

### 场景一：只有 Text —— 通过

`showSuccess()`（MainActivity.java L276-304）：`result.getType()` 为 `TEXT` → 类型标签显示"Text"；`hasText()` 为 true → Text 区显示内容；`hasUrl()` 为 false → URL 区保持占位符"—"。展示正确。

### 场景二：只有 URI —— 通过

类型为 `URL` → 类型标签显示"URL"；`hasText()` 为 false → Text 区占位符；`hasUrl()` 为 true → URL 区显示完整 URI。展示正确。

### 场景三：Text + URI 同时存在 —— 通过（含历史缺陷修复确认）

当前代码（MainActivity.java L292-300）：

```java
resultTextContent.setText(R.string.result_placeholder);
resultUrlContent.setText(R.string.result_placeholder);
if (result.hasText()) {
    resultTextContent.setText(displayContent(result.getTextContent()));
}
// Text 与 URI 可以同时存在，必须独立判断，避免组合结果遗漏 URL。
if (result.hasUrl()) {
    resultUrlContent.setText(displayContent(result.getUrlContent()));
}
```

两个条件为**相互独立的 `if`**，`hasText()` 与 `hasUrl()` 同时为 true 时两段内容都会写入；类型标签正确显示"Text + URL"；无提前 `return`、无互斥分支。模拟链路 `simulateAll()` 经 `NfcSimulator.simulateTextAndUri()` 构造标准双 Record Message，与真实读取共用同一解析器，结果模型为 `TEXT_AND_URL`，展示正确。

## 发现的问题

当前 HEAD 的审核范围内**未发现 P0/P1 阻塞问题**。以下按报告要求记录本轮核心缺陷的完整分析（该缺陷存在于 `65df9b7` 之前的提交，本轮已被修复，记录用于复盘）：

### 问题级别

P1（功能缺陷：组合场景结果缺失，不崩溃但业务展示不完整）。当前状态：**已修复，已验证**。

### 涉及代码

- 文件：`app/src/main/java/com/example/ainfcdemo/MainActivity.java`
- 方法：`showSuccess(NfcReadResult result, String source)`
- 缺陷版本代码（提交 `dde9db8` 及之前）：

```java
if (result.hasText()) {
    resultTextContent.setText(displayContent(result.getTextContent()));
} else if (result.hasUrl()) {
    resultUrlContent.setText(displayContent(result.getUrlContent()));
}
```

### 问题描述

`hasText()` 与 `hasUrl()` 是两个**可以同时成立**的条件，却被 `else if` 写成了互斥分支。当结果类型为 `TEXT_AND_URL` 时，`hasText()` 先命中，`else if` 分支被跳过，URL 内容永远不会写入 UI。

### 触发场景

同一次 NFC 读取（真实标签或"模拟 Text + URL"按钮）返回的 NDEF Message 同时包含 RTD_TEXT 与 RTD_URI Record。

### 实际影响

用户在缺陷版本中看到的界面：类型标签显示"Text + URL"、状态显示"读取成功"，但 **URL 内容区停留在占位符"—"**。类型声明与实际展示自相矛盾，且无任何错误提示，属于"静默丢数据"型缺陷。

### 原因分析——为什么 Build Success + Unit Test Passed 仍未发现

1. **单元测试只覆盖 nfc-reader 模块**：全部 20 个测试（NdefPayloadParserTest 14 个、NdefRecordParserTest 2 个、NfcReadResultTest 4 个）验证的是解析与结果模型。`NfcReadResultTest` 证明了 `TEXT_AND_URL` 时 `getTextContent()` 与 `getUrlContent()` **都能正确返回**——Module 层数据完全正确。
2. **缺陷位于 app 展示层，而 app 模块没有任何测试源码**（`testDebugUnitTest` 对 app 为 NO-SOURCE）。
3. 缺陷不产生编译错误、不抛异常、不影响单独 Text 与单独 URL 两个 Smoke 场景，只有通过"组合场景"的业务路径才能暴露。

这正是"Build Success + Unit Test Passed ≠ 业务逻辑正确"的实例：测试通过只能证明被测层正确，展示层的条件组合逻辑不在任何自动化测试的覆盖范围内，最终需要独立 Review 对照业务组合场景静态核对（或人工/仪器验收）才能发现。

### 修复建议（已实施，此处记录闭环）

提交 `65df9b7` 的修复是最小且正确的：去掉 `else`，两个条件独立判断，并补充中文注释说明"为什么"。Diff 仅 3 行新增 1 行删除，未夹带任何无关改动，符合协作规范"控制修改范围"的要求。

## 附带检查结果（简单检查项）

| 检查项 | 结论 |
| --- | --- |
| null/空结果处理 | 通过。`NfcReadResult` 构造即禁止双 null；展示前先写占位符再按条件覆盖；空字符串显示"（空内容）" |
| 明显崩溃风险 | 未发现。回调由 NfcReader 保证主线程投递；`updateReaderState()` 已对 `demoSwitch` 判空 |
| 生命周期 | 通过。`onPause` 停止 Reader 并取消未完成模拟任务；模式切换时 `cancelPendingSimulation()` 防止旧回调执行 |
| 用户可见文案 | 通过。全部来自 `strings.xml`，无业务代码硬编码文案 |
| 本轮相关冗余逻辑 | 未发现。修复未引入重复或废弃逻辑 |

## 验证证据

| 项目 | 结果 |
| --- | --- |
| `sh gradlew testDebugUnitTest` | BUILD SUCCESSFUL |
| 测试明细 | nfc-reader：14 + 2 + 4 = 20 个测试，failures=0，errors=0；app 模块无测试源码（NO-SOURCE） |
| Git 工作区 | 干净，仅 `docs/reports/` 未跟踪；修复已提交 `65df9b7` |
| 真机/模拟器运行 | 本轮未执行，组合场景的实际像素级展示仍需人工验收确认 |

## 最终结论

**通过。**

1. 三个场景（Text-only、URL-only、Text+URL）展示链路逐行核对正确，组合场景两个条件已为独立判断，无互斥、无提前 return。
2. 历史 P1 组合展示缺陷已由 `65df9b7` 最小修复并复核确认，修复未引入新问题。
3. 剩余事项：建议人工验收时在模拟器实际点击"模拟 Text + URL"，确认两块内容同时可见；真实 NFC 标签的组合内容读取仍需具备 NFC 的真机最终确认（不阻塞本轮通过）。
