package io.github.mengdlmole.testtools.security;

public record VerificationResult(boolean success, String message) {
    public static VerificationResult ok() { return new VerificationResult(true, "verified"); }
    public static VerificationResult failed(String message) { return new VerificationResult(false, message); }
}
