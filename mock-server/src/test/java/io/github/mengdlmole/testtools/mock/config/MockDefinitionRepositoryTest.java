package io.github.mengdlmole.testtools.mock.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.mengdlmole.testtools.workspace.TestWorkspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MockDefinitionRepositoryTest {
  @TempDir Path root;
  private Path definition;
  private Path responseBody;

  @BeforeEach
  void setUp() throws Exception {
    Files.createDirectories(root.resolve("environments"));
    Files.createDirectories(root.resolve("mocks/cases/order"));
    Files.createDirectories(root.resolve("fixtures/mock/order"));
    Files.writeString(root.resolve("workspace.yaml"), "defaultEnvironment: local\n");
    Files.writeString(
        root.resolve("environments/local.yaml"),
        """
                name: local
                baseUrl: http://127.0.0.1
                variables:
                  tenantId: local
                """);
    responseBody = root.resolve("fixtures/mock/order/response.json");
    Files.writeString(responseBody, "{\"version\":1}");
    definition = root.resolve("mocks/cases/order/query-order.yaml");
    writeValidDefinition();
  }

  @Test
  void atomicallyReplacesOnlyChangedValidCatalogs() throws Exception {
    MockDefinitionRepository repository = repository();
    MockCatalog initial = repository.current();
    assertEquals(1, initial.version());
    assertNull(repository.status().lastError());

    assertEquals(1, repository.reloadNow().version());

    Files.writeString(responseBody, "{\"version\":2}");
    MockCatalog reloaded = repository.reloadNow();

    assertEquals(2, reloaded.version());
    assertArrayEquals(
        "{\"version\":2}".getBytes(StandardCharsets.UTF_8),
        reloaded.definitions().getFirst().responseBodyFile());
  }

  @Test
  void keepsLastKnownGoodCatalogWhenReloadIsInvalid() throws Exception {
    MockDefinitionRepository repository = repository();
    MockCatalog initial = repository.current();
    Files.writeString(definition, "name: broken\nrequest: {}\n");

    MockCatalog afterFailure = repository.reloadNow();

    assertEquals(initial.version(), afterFailure.version());
    assertEquals(initial.fingerprint(), afterFailure.fingerprint());
    assertNotNull(repository.status().lastError());
    assertArrayEquals(
        "{\"version\":1}".getBytes(StandardCharsets.UTF_8),
        afterFailure.definitions().getFirst().responseBodyFile());
  }

  private MockDefinitionRepository repository() {
    return new MockDefinitionRepository(new MockWorkspace(new TestWorkspace(root)), 60_000);
  }

  private void writeValidDefinition() throws Exception {
    Files.writeString(
        definition,
        """
                name: query order
                request:
                  method: GET
                  path: /orders/1
                response:
                  status: 200
                  headers:
                    Content-Type: application/json
                  bodyFile: fixtures/mock/order/response.json
                """);
  }
}
