package io.github.localtools.testtools.security.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** Stateless UTF-8 HMAC-SHA256 calculation with explicit output encodings. */
public final class HmacSha256 {
    private static final String ALGORITHM = "HmacSHA256";

    private HmacSha256() {}

    public static byte[] sign(String secret, String content) {
        Objects.requireNonNull(secret, "secret must not be null");
        Objects.requireNonNull(content, "content must not be null");
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Cannot calculate HMAC-SHA256", error);
        }
    }

    public static String signHex(String secret, String content) {
        return HexFormat.of().formatHex(sign(secret, content));
    }

    public static String signBase64(String secret, String content) {
        return Base64.getEncoder().encodeToString(sign(secret, content));
    }
}
