package com.example.nfcreader;

/**
 * NFC 读取回调。所有方法都在主线程调用，可直接更新 UI。
 */
public interface NfcReaderCallback {
    /** 成功解析当前 NDEF Message 中受支持的 Text / URI Record。 */
    void onResult(NfcReadResult result);

    /** 标签已发现但读取或解析失败。 */
    void onError(NfcReadError error);
}
