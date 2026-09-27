package io.github.mengdlmole.testtools.mock.config;

import io.github.mengdlmole.testtools.mock.model.MockDefinition;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.AfterResponse;
import io.github.mengdlmole.testtools.security.SignContext;
import io.github.mengdlmole.testtools.workspace.EnvironmentContext;
import io.github.mengdlmole.testtools.workspace.TestWorkspace;
import io.github.mengdlmole.testtools.workspace.VariableResolver;
import io.github.mengdlmole.testtools.workspace.WorkspaceConfig;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mock-server-specific view of the shared test workspace.
 */
public final class MockWorkspace {
  private static final long MAX_RESPONSE_DELAY_MS = 60_000;
  private static final long MAX_CALLBACK_DELAY_MS = 3_600_000;
  private static final long MAX_CALLBACK_TIMEOUT_MS = 60_000;
  private static final int MAX_CALLBACK_ATTEMPTS = 10;
  private static final long MAX_RETRY_INTERVAL_MS = 60_000;
  private static final Pattern HTTP_TOKEN = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
  private final TestWorkspace files;

  public MockWorkspace(TestWorkspace files) {
    this.files = files;
  }

  public TestWorkspace files() {
    return files;
  }

  public MockServerConfig config() {
    return Files.isRegularFile(files.root().resolve("mock-server.yaml"))
        ? files.readYaml("mock-server.yaml", MockServerConfig.class)
        : new MockServerConfig(19090, 500L, true);
  }

  public List<MockDefinition> definitions() {
    List<MockDefinition> definitions =
        files.listYamlFiles("mocks").stream()
            .map(path -> validate(path, files.readYaml(path, MockDefinition.class)))
            .sorted(Comparator.comparing(mock -> mock.priority() == null ? 100 : mock.priority()))
            .toList();
    rejectDuplicateNames(definitions);
    return definitions;
  }

  public MockCatalog loadCatalog(long version) {
    WorkspaceConfig workspaceConfig = files.config();
    EnvironmentContext environment = files.environmentContext(workspaceConfig.defaultEnvironment());
    SignContext signContext = new SignContext(environment.variables(), environment.secrets());
    List<MockCatalog.LoadedMock> loaded =
        definitions().stream()
            .map(
                definition ->
                    new MockCatalog.LoadedMock(
                        definition,
                        bodyFile(definition.response().bodyFile()),
                        callbacks(definition, environment.variables())))
            .toList();
    return new MockCatalog(
        version, Instant.now(), fingerprint(loaded, signContext), signContext, loaded);
  }

  private List<MockCatalog.LoadedCallback> callbacks(
      MockDefinition definition, Map<String, String> variables) {
    if (definition.afterResponse() == null) {
      return List.of();
    }
    List<MockCatalog.LoadedCallback> callbacks = new ArrayList<>();
    for (AfterResponse callback : definition.afterResponse()) {
      String resolvedUrl =
          new VariableResolver(files.jsonMapper()).resolve(callback.request().url(), variables);
      validateCallbackUrl(definition.name() + " callback", resolvedUrl);
      callbacks.add(
          new MockCatalog.LoadedCallback(callback, bodyFile(callback.request().bodyFile())));
    }
    return List.copyOf(callbacks);
  }

  private byte[] bodyFile(String path) {
    return hasText(path) ? files.fileBytes(path) : null;
  }

  private String fingerprint(List<MockCatalog.LoadedMock> definitions, SignContext signContext) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(files.jsonMapper().writeValueAsBytes(signContext));
      for (MockCatalog.LoadedMock mock : definitions) {
        digest.update(files.jsonMapper().writeValueAsBytes(mock.definition()));
        update(digest, mock.responseBodyFile());
        for (MockCatalog.LoadedCallback callback : mock.callbacks()) {
          update(digest, callback.bodyFile());
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.io.IOException | NoSuchAlgorithmException error) {
      throw new IllegalStateException("Cannot fingerprint Mock catalog", error);
    }
  }

  private void update(MessageDigest digest, byte[] content) {
    if (content == null) {
      digest.update((byte) 0);
      return;
    }
    digest.update((byte) 1);
    digest.update(content);
  }

  private void rejectDuplicateNames(List<MockDefinition> definitions) {
    Set<String> names = new HashSet<>();
    definitions.forEach(
        definition -> {
          if (!names.add(definition.name())) {
            throw new IllegalArgumentException(
                "Duplicate Mock definition name: " + definition.name());
          }
        });
  }

  private MockDefinition validate(String fileName, MockDefinition definition) {
    String source = "Mock '" + fileName + "'";
    if (definition == null || !hasText(definition.name())) {
      throw new IllegalArgumentException(source + " name is required");
    }
    if (definition.request() == null) {
      throw new IllegalArgumentException(source + " request is required");
    }
    if (!hasText(definition.request().path())) {
      throw new IllegalArgumentException(source + " HTTP request path is required");
    }
    if (!definition.request().path().startsWith("/")) {
      throw new IllegalArgumentException(source + " HTTP request path must start with '/'");
    }
    validateHttpMethod(source + " request", definition.request().method(), false);
    validateHeaders(source + " request", definition.request().headers());
    if (definition.response() == null) {
      throw new IllegalArgumentException(source + " response is required");
    }
    int status = definition.response().status() == null ? 200 : definition.response().status();
    if (status < 100 || status > 599) {
      throw new IllegalArgumentException(source + " response status must be between 100 and 599");
    }
    validateHeaders(source + " response", definition.response().headers());
    validateRange(
        source + " response delayMs", definition.response().delayMs(), 0, MAX_RESPONSE_DELAY_MS);
    rejectBodyConflict(
        source + " response", definition.response().body(), definition.response().bodyFile());
    if (definition.afterResponse() != null) {
      for (int index = 0; index < definition.afterResponse().size(); index++) {
        validateCallback(source, definition.afterResponse().get(index), index + 1);
      }
    }
    return definition;
  }

  private void validateCallback(String source, AfterResponse callback, int index) {
    String callbackSource = source + " afterResponse[" + index + "]";
    if (callback == null) {
      throw new IllegalArgumentException(callbackSource + " must not be null");
    }
    if (callback.request() == null) {
      throw new IllegalArgumentException(callbackSource + " request is required");
    }
    if (!hasText(callback.request().url())) {
      throw new IllegalArgumentException(callbackSource + " URL is required");
    }
    if (!callback.request().url().contains("${")) {
      validateCallbackUrl(callbackSource, callback.request().url());
    }
    validateHttpMethod(callbackSource + " request", callback.request().method(), true);
    validateHeaders(callbackSource + " request", callback.request().headers());
    validateRange(callbackSource + " delayMs", callback.delayMs(), 0, MAX_CALLBACK_DELAY_MS);
    validateRange(callbackSource + " timeoutMs", callback.timeoutMs(), 1, MAX_CALLBACK_TIMEOUT_MS);
    if (callback.retry() != null) {
      validateRange(
          callbackSource + " retry.maxAttempts",
          callback.retry().maxAttempts(),
          1,
          MAX_CALLBACK_ATTEMPTS);
      validateRange(
          callbackSource + " retry.intervalMs",
          callback.retry().intervalMs(),
          0,
          MAX_RETRY_INTERVAL_MS);
    }
    rejectBodyConflict(
        callbackSource + " request", callback.request().body(), callback.request().bodyFile());
  }

  private void validateHttpMethod(String source, String method, boolean defaultAllowed) {
    if (!hasText(method)) {
      if (method != null && !defaultAllowed) {
        throw new IllegalArgumentException(source + " HTTP method must not be blank");
      }
      return;
    }
    if (!HTTP_TOKEN.matcher(method).matches()) {
      throw new IllegalArgumentException(source + " contains an invalid HTTP method: " + method);
    }
  }

  private void validateCallbackUrl(String source, String value) {
    try {
      URI uri = URI.create(value);
      boolean http =
          "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
      if (!uri.isAbsolute() || !http || uri.getHost() == null) {
        throw new IllegalArgumentException(
            source + " URL must be an absolute HTTP(S) URI: " + value);
      }
    } catch (IllegalArgumentException error) {
      if (error.getMessage() != null && error.getMessage().startsWith(source + " URL")) {
        throw error;
      }
      throw new IllegalArgumentException(source + " URL is invalid: " + value, error);
    }
  }

  private void validateHeaders(String source, Map<String, String> headers) {
    if (headers == null) {
      return;
    }
    headers.forEach(
        (name, value) -> {
          if (name == null || !HTTP_TOKEN.matcher(name).matches()) {
            throw new IllegalArgumentException(source + " contains an invalid Header name");
          }
          if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                source + " Header '" + name + "' has an invalid value");
          }
        });
  }

  private void validateRange(String source, Number value, long minimum, long maximum) {
    if (value != null && (value.longValue() < minimum || value.longValue() > maximum)) {
      throw new IllegalArgumentException(
          source + " must be between " + minimum + " and " + maximum);
    }
  }

  private void rejectBodyConflict(String source, Object body, String bodyFile) {
    if (bodyFile != null && bodyFile.isBlank()) {
      throw new IllegalArgumentException(source + " bodyFile must not be blank");
    }
    if (body != null && hasText(bodyFile)) {
      throw new IllegalArgumentException(source + " may use body or bodyFile, not both");
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
