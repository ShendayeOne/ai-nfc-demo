package com.example.nfcreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class NdefPayloadParserTest {
    @Test
    public void parsesEnglishText() throws Exception {
        assertEquals("Hello NFC", NdefParser.parseTextPayload(textPayload("en", "Hello NFC")));
    }

    @Test
    public void parsesChineseText() throws Exception {
        assertEquals("欢迎使用 NFC Reader", NdefParser.parseTextPayload(textPayload("zh", "欢迎使用 NFC Reader")));
    }

    @Test
    public void parsesEmptyText() throws Exception {
        assertEquals("", NdefParser.parseTextPayload(textPayload("zh", "")));
    }

    @Test
    public void parsesUtf16Text() throws Exception {
        byte[] language = "en".getBytes(StandardCharsets.US_ASCII);
        byte[] text = "NFC 演示".getBytes(StandardCharsets.UTF_16);
        byte[] payload = new byte[1 + language.length + text.length];
        payload[0] = (byte) (0x80 | language.length);
        System.arraycopy(language, 0, payload, 1, language.length);
        System.arraycopy(text, 0, payload, 1 + language.length, text.length);
        assertEquals("NFC 演示", NdefParser.parseTextPayload(payload));
    }

    @Test
    public void rejectsEmptyTextPayload() {
        assertError(NfcReadError.MALFORMED_DATA, () -> NdefParser.parseTextPayload(new byte[0]));
    }

    @Test
    public void rejectsTextLanguageLengthPastPayload() {
        assertError(NfcReadError.MALFORMED_DATA,
                () -> NdefParser.parseTextPayload(new byte[]{0x05, 'e', 'n'}));
    }

    @Test
    public void rejectsMalformedUtf8Text() {
        assertError(NfcReadError.MALFORMED_DATA,
                () -> NdefParser.parseTextPayload(new byte[]{0x02, 'z', 'h', (byte) 0xC3, 0x28}));
    }

    @Test
    public void parsesHttpsPrefix() throws Exception {
        assertEquals("https://www.example.com/nfc/demo",
                NdefParser.parseUriPayload(uriPayload(2, "example.com/nfc/demo")));
    }

    @Test
    public void parsesHttpPrefix() throws Exception {
        assertEquals("http://example.com", NdefParser.parseUriPayload(uriPayload(3, "example.com")));
    }

    @Test
    public void parsesUncompressedHttps() throws Exception {
        assertEquals("https://example.com/path",
                NdefParser.parseUriPayload(uriPayload(0, "https://example.com/path")));
    }

    @Test
    public void parsesOtherStandardUriPrefix() throws Exception {
        assertEquals("mailto:demo@example.com",
                NdefParser.parseUriPayload(uriPayload(6, "demo@example.com")));
    }

    @Test
    public void rejectsEmptyUriPayload() {
        assertError(NfcReadError.MALFORMED_DATA, () -> NdefParser.parseUriPayload(new byte[0]));
    }

    @Test
    public void rejectsUnknownUriPrefix() {
        assertError(NfcReadError.MALFORMED_DATA,
                () -> NdefParser.parseUriPayload(uriPayload(127, "example.com")));
    }

    @Test
    public void rejectsMalformedUtf8Uri() {
        assertError(NfcReadError.MALFORMED_DATA,
                () -> NdefParser.parseUriPayload(new byte[]{0x04, (byte) 0xC3, 0x28}));
    }

    private static byte[] textPayload(String languageCode, String text) {
        byte[] language = languageCode.getBytes(StandardCharsets.US_ASCII);
        byte[] value = text.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[1 + language.length + value.length];
        payload[0] = (byte) language.length;
        System.arraycopy(language, 0, payload, 1, language.length);
        System.arraycopy(value, 0, payload, 1 + language.length, value.length);
        return payload;
    }

    private static byte[] uriPayload(int prefix, String suffix) {
        byte[] value = suffix.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[1 + value.length];
        payload[0] = (byte) prefix;
        System.arraycopy(value, 0, payload, 1, value.length);
        return payload;
    }

    private static void assertError(NfcReadError expected, ThrowingRunnable action) {
        try {
            action.run();
            fail("Expected ParseException");
        } catch (NdefParser.ParseException exception) {
            assertEquals(expected, exception.getError());
        }
    }

    private interface ThrowingRunnable {
        void run() throws NdefParser.ParseException;
    }
}
