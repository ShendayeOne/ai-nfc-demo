package com.example.nfcreader;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Text / URI 的统一 NDEF 解析器。
 * payload 方法不调用 Android 运行时，核心边界可直接用普通 JVM 单元测试验证。
 */
final class NdefParser {
    private static final short TNF_WELL_KNOWN = 0x01;
    private static final byte[] TYPE_TEXT = {'T'};
    private static final byte[] TYPE_URI = {'U'};

    // NFC Forum URI Record Type Definition 规定的完整前缀表，数组下标就是 payload 首字节。
    private static final String[] URI_PREFIXES = {
            "", "http://www.", "https://www.", "http://", "https://", "tel:",
            "mailto:", "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://",
            "sftp://", "smb://", "nfs://", "ftp://", "dav://", "news:",
            "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:", "sips:",
            "tftp:", "btspp://", "btl2cap://", "btgoep://", "tcpobex://",
            "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:",
            "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:"
    };

    NfcReadResult parse(NdefMessage message) throws ParseException {
        if (message == null || message.getRecords().length == 0) {
            throw new ParseException(NfcReadError.EMPTY_MESSAGE);
        }

        String textContent = null;
        String urlContent = null;
        // 同一标签可能同时写入 Text 和 URI；每种类型只取第一条，保持结果模型简单确定。
        for (NdefRecord record : message.getRecords()) {
            try {
                NfcReadResult recordResult = parseRecord(
                        record.getTnf(),
                        record.getType(),
                        record.getPayload()
                );
                if (recordResult.hasText() && textContent == null) {
                    textContent = recordResult.getTextContent();
                }
                if (recordResult.hasUrl() && urlContent == null) {
                    urlContent = recordResult.getUrlContent();
                }
            } catch (ParseException exception) {
                if (exception.getError() != NfcReadError.UNSUPPORTED_RECORD) {
                    throw exception;
                }
            }
        }
        if (textContent == null && urlContent == null) {
            throw new ParseException(NfcReadError.UNSUPPORTED_RECORD);
        }
        return new NfcReadResult(textContent, urlContent);
    }

    NfcReadResult parseRecord(short tnf, byte[] type, byte[] payload) throws ParseException {
        if (tnf != TNF_WELL_KNOWN) {
            throw new ParseException(NfcReadError.UNSUPPORTED_RECORD);
        }
        if (Arrays.equals(type, TYPE_TEXT)) {
            return new NfcReadResult(parseTextPayload(payload), null);
        }
        if (Arrays.equals(type, TYPE_URI)) {
            return new NfcReadResult(null, parseUriPayload(payload));
        }
        throw new ParseException(NfcReadError.UNSUPPORTED_RECORD);
    }

    static String parseTextPayload(byte[] payload) throws ParseException {
        if (payload == null || payload.length == 0) {
            throw new ParseException(NfcReadError.MALFORMED_DATA);
        }

        int status = payload[0] & 0xFF;
        int languageLength = status & 0x3F;
        int textStart = 1 + languageLength;
        if (textStart > payload.length) {
            throw new ParseException(NfcReadError.MALFORMED_DATA);
        }

        // RTD_TEXT 状态字节最高位决定 UTF-8/UTF-16，低 6 位是语言码长度。
        Charset charset = (status & 0x80) == 0 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16;
        return decodeStrict(payload, textStart, payload.length - textStart, charset);
    }

    static String parseUriPayload(byte[] payload) throws ParseException {
        if (payload == null || payload.length == 0) {
            throw new ParseException(NfcReadError.MALFORMED_DATA);
        }

        int prefixCode = payload[0] & 0xFF;
        if (prefixCode >= URI_PREFIXES.length) {
            throw new ParseException(NfcReadError.MALFORMED_DATA);
        }

        String suffix = decodeStrict(payload, 1, payload.length - 1, StandardCharsets.UTF_8);
        return URI_PREFIXES[prefixCode] + suffix;
    }

    private static String decodeStrict(byte[] value, int offset, int length, Charset charset)
            throws ParseException {
        try {
            // 默认解码会用替换字符吞掉坏数据；这里严格失败，避免把损坏标签误报为读取成功。
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value, offset, length))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new ParseException(NfcReadError.MALFORMED_DATA);
        }
    }

    static final class ParseException extends Exception {
        private final NfcReadError error;

        ParseException(NfcReadError error) {
            super(error.name());
            this.error = error;
        }

        NfcReadError getError() {
            return error;
        }
    }
}
