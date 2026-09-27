package io.github.mengdlmole.testtools.mock.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mengdlmole.testtools.workspace.TestWorkspace;
import io.github.mengdlmole.testtools.workspace.WorkspaceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MockWorkspaceTest {
  @TempDir Path temporary;

  @Test
  void rejectsProtocolFieldsInsteadOfPretendingToSupportRpc() throws Exception {
    Files.createDirectories(temporary.resolve("mocks/cases/rpc"));
    Files.writeString(
        temporary.resolve("mocks/cases/rpc/rpc.yaml"),
        """
                name: future rpc
                protocol: grpc
                request:
                  method: GET
                  path: /health
                response:
                  status: 500
                """);

    MockWorkspace mocks = new MockWorkspace(new TestWorkspace(temporary));

    assertThrows(WorkspaceException.class, mocks::definitions);
  }

  @Test
  void loadsMockDefinitionsRecursivelyByCategoryAndDomain() throws Exception {
    Files.createDirectories(temporary.resolve("mocks/examples"));
    Files.createDirectories(temporary.resolve("mocks/cases/order"));
    Files.writeString(
        temporary.resolve("mocks/examples/health.yaml"),
        """
                name: health example
                request:
                  method: GET
                  path: /health
                response:
                  status: 200
                """);
    Files.writeString(
        temporary.resolve("mocks/cases/order/create.yaml"),
        """
                name: create order
                request:
                  method: POST
                  path: /orders
                response:
                  status: 201
                """);

    var definitions = new MockWorkspace(new TestWorkspace(temporary)).definitions();

    assertEquals(2, definitions.size());
    assertEquals(
        Set.of("health example", "create order"),
        definitions.stream().map(definition -> definition.name()).collect(Collectors.toSet()));
  }

  @Test
  void rejectsConflictingMockAndCallbackBodySources() throws Exception {
    Files.createDirectories(temporary.resolve("mocks"));
    Files.writeString(
        temporary.resolve("mocks/conflict.yaml"),
        """
                name: conflicting bodies
                request:
                  path: /conflict
                response:
                  body: {status: ok}
                  bodyFile: fixtures/response.json
                """);

    MockWorkspace mocks = new MockWorkspace(new TestWorkspace(temporary));
    assertThrows(IllegalArgumentException.class, mocks::definitions);

    Files.writeString(
        temporary.resolve("mocks/conflict.yaml"),
        """
                name: conflicting callback bodies
                request:
                  path: /conflict
                response:
                  status: 200
                afterResponse:
                  - request:
                      url: http://localhost/callback
                      body: {status: ok}
                      bodyFile: fixtures/callback.json
                """);

    assertThrows(IllegalArgumentException.class, mocks::definitions);
  }
}
