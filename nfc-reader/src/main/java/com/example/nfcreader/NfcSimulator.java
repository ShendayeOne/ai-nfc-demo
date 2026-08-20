package com.example.nfcreader;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

import java.util.Objects;

/** 使用标准 NDEF Record 经过真实解析链路生成模拟读取结果。 */
public final class NfcSimulator {
    private NfcSimulator() {
    }

    /** 构造标准 RTD_TEXT 并经过统一解析器返回结果。 */
    public static NfcReadResult simulateText(String languageCode, String text) {
        Objects.requireNonNull(languageCode, "languageCode");
        Objects.requireNonNull(text, "text");
        NdefRecord record = NdefRecord.createTextRecord(languageCode, text);
        return parse(new NdefMessage(record));
    }

    /** 构造标准 RTD_URI 并经过统一解析器返回结果。 */
    public static NfcReadResult simulateUri(String uri) {
        Objects.requireNonNull(uri, "uri");
        NdefRecord record = NdefRecord.createUri(uri);
        return parse(new NdefMessage(record));
    }

    /** 构造同时包含 RTD_TEXT 与 RTD_URI 的标准 Message，并经过统一解析器返回结果。 */
    public static NfcReadResult simulateTextAndUri(
            String languageCode,
            String text,
            String uri
    ) {
        Objects.requireNonNull(languageCode, "languageCode");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(uri, "uri");
        return parse(new NdefMessage(new NdefRecord[]{
                NdefRecord.createTextRecord(languageCode, text),
                NdefRecord.createUri(uri)
        }));
    }

    private static NfcReadResult parse(NdefMessage message) {
        try {
            return new NdefParser().parse(message);
        } catch (NdefParser.ParseException exception) {
            throw new IllegalArgumentException("Invalid simulated NDEF message", exception);
        }
    }
}
