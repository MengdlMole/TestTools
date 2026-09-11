package io.github.localtools.testtools.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Constant-time comparisons used when checking signatures or authentication values. */
public final class ConstantTime {
    private ConstantTime() {}

    public static boolean equalsUtf8(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
