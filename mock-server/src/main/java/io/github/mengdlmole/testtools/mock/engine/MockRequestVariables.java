package io.github.mengdlmole.testtools.mock.engine;

import io.github.mengdlmole.testtools.http.transport.RequestSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the variables shared by response rendering and callback rendering.
 */
public final class MockRequestVariables {
  private MockRequestVariables() {}

  public static Map<String, String> create(
      RequestSnapshot request, Map<String, String> baseVariables) {
    Map<String, String> values = new LinkedHashMap<>(baseVariables);
    values.put("request.body", request.bodyText());
    values.put("request.method", request.method());
    values.put("request.path", request.uri().getPath());
    values.put("random.uuid", UUID.randomUUID().toString());
    return values;
  }
}
