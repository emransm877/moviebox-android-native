package org.emransm.moviebox.phone;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class Utf8JsonTest {
    @Test public void jsonMetadataAndInternationalTextSurviveExactly() throws Exception {
        String json = "{\"title\":\"Café · L’été – “MovieBox” বাংলা العربية 日本語 São Ângela Ã\","
                + "\"subjectId\":\"opaque:\\u00c2\\u00b7\",\"url\":\"https://example.test/\\u00c3\\u00a9?token=unchanged\","
                + "\"subtitle\":\"Literal \\u00e2\\u20ac\\u2122 cue\"}";
        assertEquals(json, Utf8Json.decode(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void actualUtf8BytesDoNotBecomeLatin1Punctuation() throws Exception {
        byte[] payload = new byte[]{0x43,0x61,0x66,(byte)0xc3,(byte)0xa9,0x20,(byte)0xc2,(byte)0xb7,
                0x20,(byte)0xe2,(byte)0x80,(byte)0x99,0x20,(byte)0xe2,(byte)0x80,(byte)0x93};
        assertEquals("Café · ’ –", Utf8Json.decode(payload));
    }

    @Test public void malformedUtf8NeverBecomesPersistedReplacementText() throws Exception {
        for (byte[] invalid : new byte[][]{
                {(byte)0xc3,0x28}, {(byte)0xe2,(byte)0x82}, {(byte)0xc0,(byte)0xaf},
                {(byte)0xed,(byte)0xa0,(byte)0x80}}) {
            try { Utf8Json.decode(invalid); fail("Malformed JSON bytes must not be silently replaced"); }
            catch (CharacterCodingException expected) { }
        }
    }
}
