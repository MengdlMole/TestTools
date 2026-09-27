package io.github.mengdlmole.testtools.mock.config;

/**
 * Local Mock Server process settings.
 *
 * @param port loopback HTTP port
 * @param reloadIntervalMs minimum interval between automatic catalog checks
 * @param maskSensitiveData whether persisted Mock call metadata hides URI credentials
 */
public record MockServerConfig(Integer port, Long reloadIntervalMs, Boolean maskSensitiveData) {
  public int resolvedPort() {
    return port == null ? 19090 : port;
  }

  public long resolvedReloadIntervalMs() {
    return reloadIntervalMs == null ? 500 : Math.max(0, reloadIntervalMs);
  }

  public boolean resolvedMaskSensitiveData() {
    return maskSensitiveData == null || maskSensitiveData;
  }
}
