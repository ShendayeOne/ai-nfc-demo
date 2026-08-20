package com.example.nfcreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class NfcReadResultTest {
    @Test
    public void representsTextOnlyIncludingEmptyText() {
        NfcReadResult result = new NfcReadResult("", null);
        assertEquals(NfcReadResult.Type.TEXT, result.getType());
        assertTrue(result.hasText());
        assertFalse(result.hasUrl());
        assertEquals("", result.getTextContent());
        assertNull(result.getUrlContent());
    }

    @Test
    public void representsUrlOnly() {
        NfcReadResult result = new NfcReadResult(null, "https://example.com");
        assertEquals(NfcReadResult.Type.URL, result.getType());
        assertFalse(result.hasText());
        assertTrue(result.hasUrl());
    }

    @Test
    public void representsTextAndUrlTogether() {
        NfcReadResult result = new NfcReadResult("hello", "https://example.com");
        assertEquals(NfcReadResult.Type.TEXT_AND_URL, result.getType());
        assertTrue(result.hasText());
        assertTrue(result.hasUrl());
    }

    @Test
    public void rejectsResultWithoutSupportedContent() {
        try {
            new NfcReadResult(null, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 两项都不存在不是成功结果，应由 Reader 走 UNSUPPORTED_RECORD 错误回调。
        }
    }
}
