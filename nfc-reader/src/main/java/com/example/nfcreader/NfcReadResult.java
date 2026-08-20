package com.example.nfcreader;

/** 一次 NDEF 读取中解析到的 Text 与 URL 内容。 */
public final class NfcReadResult {
    /** 当前 Module 支持的内容类型。 */
    public enum Type {
        /** NFC Forum RTD_TEXT。 */
        TEXT,
        /** NFC Forum RTD_URI；名称按 Demo 展示约定使用 URL。 */
        URL,
        /** 同一 NDEF Message 同时包含 Text 与 URI。 */
        TEXT_AND_URL
    }

    private final String textContent;
    private final String urlContent;

    /** 创建不可变结果；两项至少存在一项，null 表示该类型未出现在 Message 中。 */
    public NfcReadResult(String textContent, String urlContent) {
        if (textContent == null && urlContent == null) {
            throw new IllegalArgumentException("textContent and urlContent cannot both be null");
        }
        this.textContent = textContent;
        this.urlContent = urlContent;
    }

    /** 返回 Text、URL 或两者同时存在。 */
    public Type getType() {
        if (textContent != null && urlContent != null) {
            return Type.TEXT_AND_URL;
        }
        return textContent != null ? Type.TEXT : Type.URL;
    }

    /** 是否包含 RTD_TEXT；空 Text 也视为存在。 */
    public boolean hasText() {
        return textContent != null;
    }

    /** 是否包含 RTD_URI。 */
    public boolean hasUrl() {
        return urlContent != null;
    }

    /** 返回解码后的 Text；Message 不含 Text 时返回 null。 */
    public String getTextContent() {
        return textContent;
    }

    /** 返回完整 URI；Message 不含 URI 时返回 null。 */
    public String getUrlContent() {
        return urlContent;
    }
}
