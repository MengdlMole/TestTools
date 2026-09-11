package io.github.mengdlmole.testtools.mock.config;

public record MockServerConfig(Integer port) {
    public int resolvedPort() { return port == null ? 19090 : port; }
}
