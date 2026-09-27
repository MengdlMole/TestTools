package io.github.mengdlmole.testtools.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestWorkspaceTest {
  @TempDir Path root;

  @Test
  void readsNamedYmlFilesFromAnyFeatureDirectory() throws Exception {
    Files.createDirectories(root.resolve("feature"));
    Files.writeString(root.resolve("feature/example.yml"), "name: example\n");
    TestWorkspace workspace = new TestWorkspace(root);

    assertEquals("example", workspace.listYamlNames("feature").getFirst());
    assertEquals(
        "example", workspace.readNamedYaml("feature", "example", NamedConfig.class).name());
  }

  @Test
  void listsNestedYamlFilesAsWorkspaceRelativePaths() throws Exception {
    Files.createDirectories(root.resolve("mocks/examples"));
    Files.createDirectories(root.resolve("mocks/cases/order"));
    Files.writeString(root.resolve("mocks/examples/health.yaml"), "name: health\n");
    Files.writeString(root.resolve("mocks/cases/order/create.yml"), "name: create order\n");
    Files.writeString(root.resolve("mocks/README.md"), "documentation\n");

    assertEquals(
        java.util.List.of("mocks/cases/order/create.yml", "mocks/examples/health.yaml"),
        new TestWorkspace(root).listYamlFiles("mocks"));
  }

  @Test
  void recursiveYamlListingIgnoresSymbolicLinks(@TempDir Path outside) throws Exception {
    Files.createDirectories(root.resolve("mocks"));
    Files.writeString(outside.resolve("outside.yaml"), "name: outside\n");
    createSymbolicLinkIfSupported(
        root.resolve("mocks/outside.yaml"), outside.resolve("outside.yaml"));

    assertEquals(java.util.List.of(), new TestWorkspace(root).listYamlFiles("mocks"));
  }

  @Test
  void rejectsUnknownYamlFieldsSoTyposDoNotSilentlyPass() throws Exception {
    Files.writeString(
        root.resolve("workspace.yaml"), "defaultEnvironment: local\nunknownField: typo\n");
    assertThrows(WorkspaceException.class, () -> new TestWorkspace(root).config());
  }

  @Test
  void resolvesGlobalAndCaseOwnedJsonFixturesAndRejectsTraversal() throws Exception {
    Files.createDirectories(root.resolve("fixtures/api-tests/global"));
    Files.createDirectories(root.resolve("fixtures/api-tests/cases/create-order"));
    Files.writeString(
        root.resolve("fixtures/api-tests/global/common.json"), "{\"scope\":\"global\"}");
    Files.writeString(
        root.resolve("fixtures/api-tests/cases/create-order/request.json"), "{\"scope\":\"case\"}");
    TestWorkspace workspace = new TestWorkspace(root);

    assertEquals(
        "global",
        workspace.readJson(workspace.apiGlobalJsonFile("common.json")).path("scope").asText());
    assertEquals(
        "case",
        workspace
            .readJson(workspace.apiCaseJsonFile("create-order", "request.json"))
            .path("scope")
            .asText());
    assertThrows(
        IllegalArgumentException.class, () -> workspace.apiGlobalJsonFile("../secret.json"));
    assertThrows(
        IllegalArgumentException.class,
        () -> workspace.apiCaseJsonFile("create-order", "../other.json"));
  }

  @Test
  void rejectsJsonFixtureDirectorySymlinkedOutsideWorkspace(@TempDir Path outside)
      throws Exception {
    Files.writeString(outside.resolve("request.json"), "{}");
    Files.createDirectories(root.resolve("fixtures/api-tests"));
    createSymbolicLinkIfSupported(root.resolve("fixtures/api-tests/global"), outside);

    assertThrows(
        IllegalArgumentException.class,
        () -> new TestWorkspace(root).apiGlobalJsonFile("request.json"));
  }

  @Test
  void rejectsBodyFileSymlinkedOutsideWorkspace(@TempDir Path outside) throws Exception {
    Files.writeString(outside.resolve("secret.txt"), "outside workspace");
    Files.createDirectories(root.resolve("fixtures"));
    createSymbolicLinkIfSupported(
        root.resolve("fixtures/external-secret.txt"), outside.resolve("secret.txt"));

    TestWorkspace workspace = new TestWorkspace(root);

    assertThrows(
        IllegalArgumentException.class, () -> workspace.fileBytes("fixtures/external-secret.txt"));
  }

  @Test
  void writesNestedResultInsideWorkspace() throws Exception {
    TestWorkspace workspace = new TestWorkspace(root);

    Path result = workspace.writeResult("orders/create.json", Map.of("status", "ok"));

    assertEquals(root.resolve("results/orders/create.json").toRealPath(), result.toRealPath());
    assertEquals("ok", workspace.readJson(result).path("status").asText());
  }

  @Test
  void rejectsResultsDirectorySymlinkedOutsideWorkspace(@TempDir Path outside) throws Exception {
    createSymbolicLinkIfSupported(root.resolve("results"), outside);

    assertThrows(
        IllegalArgumentException.class,
        () -> new TestWorkspace(root).writeResult("result.json", Map.of("secret", "value")));
  }

  @Test
  void rejectsResultParentSymlinkedOutsideWorkspace(@TempDir Path outside) throws Exception {
    Files.createDirectories(root.resolve("results"));
    createSymbolicLinkIfSupported(root.resolve("results/orders"), outside);

    assertThrows(
        IllegalArgumentException.class,
        () -> new TestWorkspace(root).writeResult("orders/result.json", Map.of()));
  }

  @Test
  void rejectsExistingResultFileSymlink(@TempDir Path outside) throws Exception {
    Files.createDirectories(root.resolve("results"));
    Files.writeString(outside.resolve("captured.json"), "original");
    createSymbolicLinkIfSupported(
        root.resolve("results/result.json"), outside.resolve("captured.json"));

    assertThrows(
        IllegalArgumentException.class,
        () -> new TestWorkspace(root).writeResult("result.json", Map.of("secret", "value")));
    assertEquals("original", Files.readString(outside.resolve("captured.json")));
  }

  @Test
  void resolvesEnvironmentVariablesAndSecretsWithDocumentedPrecedence() throws Exception {
    Files.createDirectories(root.resolve("environments"));
    Files.createDirectories(root.resolve("secrets"));
    Files.writeString(
        root.resolve("workspace.yaml"),
        """
                defaultEnvironment: local
                variables:
                  shared: workspace
                  workspaceOnly: one
                """);
    Files.writeString(
        root.resolve("environments/local.yaml"),
        """
                name: local
                baseUrl: http://localhost
                secretRef: demo
                variables:
                  shared: environment
                  environmentOnly: two
                """);
    Files.writeString(
        root.resolve("secrets/local-secrets.yaml"),
        """
                secrets:
                  demo:
                    appSecret: secret
                """);
    Map<String, Object> overrides = new LinkedHashMap<>();
    overrides.put("shared", "invocation");
    overrides.put("empty", null);

    EnvironmentContext context = new TestWorkspace(root).environmentContext("local", overrides);

    assertEquals("invocation", context.variables().get("shared"));
    assertEquals("one", context.variables().get("workspaceOnly"));
    assertEquals("two", context.variables().get("environmentOnly"));
    assertEquals("", context.variables().get("empty"));
    assertEquals("secret", context.secrets().get("appSecret"));
    assertThrows(
        UnsupportedOperationException.class, () -> context.variables().put("other", "value"));
  }

  @Test
  void explicitWorkspaceLocationOverridesSystemProperty(@TempDir Path explicit) {
    String previous = System.getProperty("testtools.workspace");
    try {
      System.setProperty("testtools.workspace", root.toString());
      assertEquals(
          explicit.toAbsolutePath().normalize(), WorkspaceLocator.locate(explicit.toString()));
    } finally {
      if (previous == null) {
        System.clearProperty("testtools.workspace");
      } else {
        System.setProperty("testtools.workspace", previous);
      }
    }
  }

  private static void createSymbolicLinkIfSupported(Path link, Path target) throws IOException {
    try {
      Files.createSymbolicLink(link, target);
    } catch (UnsupportedOperationException | IOException error) {
      assumeTrue(false, "Symbolic links are not supported on this platform: " + error.getMessage());
    }
  }

  private record NamedConfig(String name) {}
}
