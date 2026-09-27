package io.github.mengdlmole.testtools.mock.engine;

import io.github.mengdlmole.testtools.http.transport.RequestSnapshot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Framework-neutral HTTP request consumed by the Mock engine.
 *
 * @param snapshot method, URI, headers, and body
 * @param query decoded query parameters including repeated values
 */
public record MockRequest(RequestSnapshot snapshot, Map<String, List<String>> query) {
  public MockRequest {
    if (snapshot == null) {
      throw new IllegalArgumentException("Request snapshot is required");
    }
    query = immutableValues(query);
  }

  public String firstQuery(String name) {
    return query.getOrDefault(name, List.of()).stream().findFirst().orElse(null);
  }

  private static Map<String, List<String>> immutableValues(Map<String, List<String>> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    Map<String, List<String>> copy = new LinkedHashMap<>();
    source.forEach(
        (name, values) -> copy.put(name, values == null ? List.of() : List.copyOf(values)));
    return Collections.unmodifiableMap(copy);
  }
}
