package com.example.nfcreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class NdefRecordParserTest {
    private final NdefParser parser = new NdefParser();

    @Test
    public void rejectsUnsupportedTnf() {
        assertUnsupported(() -> parser.parseRecord((short) 0x02, new byte[]{'T'}, new byte[]{0}));
    }

    @Test
    public void rejectsUnsupportedWellKnownType() {
        assertUnsupported(() -> parser.parseRecord((short) 0x01, new byte[]{'S', 'p'}, new byte[]{0}));
    }

    private static void assertUnsupported(ThrowingRunnable action) {
        try {
            action.run();
            fail("Expected ParseException");
        } catch (NdefParser.ParseException exception) {
            assertEquals(NfcReadError.UNSUPPORTED_RECORD, exception.getError());
        }
    }

    private interface ThrowingRunnable {
        void run() throws NdefParser.ParseException;
    }
}
