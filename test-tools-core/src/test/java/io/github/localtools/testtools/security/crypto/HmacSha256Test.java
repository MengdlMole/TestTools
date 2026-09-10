package io.github.localtools.testtools.security.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HmacSha256Test {
    @Test
    void createsKnownUtf8HexAndBase64Values() {
        String secret = "key";
        String content = "The quick brown fox jumps over the lazy dog";

        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                HmacSha256.signHex(secret, content));
        assertEquals("97yD9DBThCSxMpjmqm+xQ+9NWaFJRhdZl0edvC0aPNg=",
                HmacSha256.signBase64(secret, content));
    }

    @Test
    void rejectsNullInputsExplicitly() {
        assertThrows(NullPointerException.class, () -> HmacSha256.signHex(null, "content"));
        assertThrows(NullPointerException.class, () -> HmacSha256.signHex("secret", null));
    }
}
