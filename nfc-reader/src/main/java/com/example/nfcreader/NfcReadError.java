package com.example.nfcreader;

/** NFC 读取失败的稳定错误类型，调用方应自行映射用户文案。 */
public enum NfcReadError {
    /** 标签无法通过 Android Ndef 技术读取。 */
    NOT_NDEF,
    /** 标签存在 NDEF 能力，但消息为空。 */
    EMPTY_MESSAGE,
    /** 消息中没有当前支持的 Text 或 URI Record。 */
    UNSUPPORTED_RECORD,
    /** payload 长度、前缀或字符编码不合法。 */
    MALFORMED_DATA,
    /** NFC I/O 或系统状态导致读取失败。 */
    READ_FAILED
}
