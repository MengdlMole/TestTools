package io.github.localtools.testtools.mock.config;

public record MockServerConfig(Integer port) {
    public int resolvedPort() { return port == null ? 19090 : port; }
}
