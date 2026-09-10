package io.github.localtools.testtools.workspace;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Safe, domain-neutral access to a local test workspace.
 * YAML case and Mock schemas belong to their feature modules, not to core.
 */
public final class TestWorkspace {
    private final Path root;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public TestWorkspace(Path root) {
        this(root, new ObjectMapper().findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
    }

    public TestWorkspace(Path root, ObjectMapper jsonMapper) {
        this.root = root.toAbsolutePath().normalize();
        this.yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.jsonMapper = jsonMapper;
    }

    public Path root() { return root; }
    public ObjectMapper jsonMapper() { return jsonMapper; }
    public WorkspaceConfig config() { return readYaml("workspace.yaml", WorkspaceConfig.class); }

    public EnvironmentConfig environment(String name) {
        return readNamedYaml("environments", name, EnvironmentConfig.class);
    }

    public Map<String, String> secrets(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) return Map.of();
        Path path = resolve("secrets/local-secrets.yaml");
        if (!Files.exists(path)) return Map.of();
        SecretDocument file = readYaml("secrets/local-secrets.yaml", SecretDocument.class);
        return file.secrets() == null ? Map.of() : file.secrets().getOrDefault(secretRef, Map.of());
    }

    public <T> T readYaml(String relativePath, Class<T> type) {
        try {
            return yamlMapper.readValue(resolve(relativePath).toFile(), type);
        } catch (IOException error) {
            throw new WorkspaceException("Cannot read YAML: " + relativePath, error);
        }
    }

    public <T> T readNamedYaml(String directory, String name, Class<T> type) {
        try {
            return yamlMapper.readValue(resolveNamedYaml(directory, name).toFile(), type);
        } catch (IOException error) {
            throw new WorkspaceException("Cannot read YAML: " + directory + "/" + name, error);
        }
    }

    public <T> T parseYaml(String yaml, Class<T> type) {
        try {
            return yamlMapper.readValue(yaml, type);
        } catch (IOException error) {
            throw new WorkspaceException("Invalid YAML", error);
        }
    }

    public List<String> listYamlNames(String directory) {
        Path path = resolve(directory);
        if (!Files.isDirectory(path)) return List.of();
        try (var files = Files.list(path)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".yaml") || name.endsWith(".yml"))
                    .map(name -> name.substring(0, name.lastIndexOf('.')))
                    .distinct().sorted().toList();
        } catch (IOException error) {
            throw new WorkspaceException("Cannot list YAML directory: " + directory, error);
        }
    }

    public String readNamedYamlText(String directory, String name) {
        try {
            return Files.readString(resolveNamedYaml(directory, name));
        } catch (IOException error) {
            throw new WorkspaceException("Cannot read YAML: " + directory + "/" + name, error);
        }
    }

    public synchronized void writeNamedYamlText(String directory, String name, String yaml) {
        writeAtomically(resolve(directory).resolve(safeName(name) + ".yaml"),
                yaml.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] fileBytes(String relativePath) {
        try {
            return Files.readAllBytes(resolve(relativePath));
        } catch (IOException error) {
            throw new WorkspaceException("Cannot read workspace file: " + relativePath, error);
        }
    }

    public Path globalJsonFile(String relativeFile) {
        return jsonFixture(resolve("fixtures/global"), relativeFile);
    }

    public Path caseJsonFile(String caseName, String relativeFile) {
        return jsonFixture(resolve("fixtures/cases").resolve(safeName(caseName)), relativeFile);
    }

    public JsonNode readJson(Path path) {
        try {
            Path realRoot = root.toRealPath();
            Path realPath = path.toAbsolutePath().normalize().toRealPath();
            if (!realPath.startsWith(realRoot)) throw new IllegalArgumentException("JSON file must remain inside workspace");
            JsonNode result = jsonMapper.readTree(realPath.toFile());
            if (result == null) throw new WorkspaceException("JSON fixture must not be empty: " + path);
            return result;
        } catch (IOException error) {
            throw new WorkspaceException("Cannot read JSON file: " + path, error);
        }
    }

    public synchronized Path writeResult(String relativePath, Object result) {
        try {
            Path target = resolve("results").resolve(relativePath).normalize();
            ensureInside(resolve("results"), target, "Invalid result path");
            writeAtomically(target, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result));
            return target;
        } catch (IOException error) {
            throw new WorkspaceException("Cannot save result", error);
        }
    }

    public List<String> resultFiles(int limit) {
        Path resultRoot = resolve("results");
        if (!Files.isDirectory(resultRoot)) return List.of();
        try (var files = Files.walk(resultRoot)) {
            return files.filter(Files::isRegularFile).map(resultRoot::relativize).map(Path::toString)
                    .sorted(java.util.Comparator.reverseOrder()).limit(Math.max(1, Math.min(limit, 500))).toList();
        } catch (IOException error) {
            throw new WorkspaceException("Cannot list results", error);
        }
    }

    public JsonNode result(String relativePath) {
        try {
            Path target = resolve("results").resolve(relativePath).normalize();
            ensureInside(resolve("results"), target, "Invalid result path");
            return jsonMapper.readTree(target.toFile());
        } catch (IOException error) {
            throw new WorkspaceException("Cannot read result: " + relativePath, error);
        }
    }

    private Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        ensureInside(root, path, "Workspace path must remain inside workspace");
        return path;
    }

    private Path resolveNamedYaml(String directory, String name) {
        Path base = resolve(directory);
        String safe = safeName(name);
        Path yaml = base.resolve(safe + ".yaml");
        if (Files.isRegularFile(yaml)) return yaml;
        Path yml = base.resolve(safe + ".yml");
        return Files.isRegularFile(yml) ? yml : yaml;
    }

    private String safeName(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid file name");
        }
        return name;
    }

    private Path jsonFixture(Path fixtureRoot, String relativeFile) {
        if (relativeFile == null || relativeFile.isBlank()) {
            throw new IllegalArgumentException("JSON fixture name is required");
        }
        Path normalizedRoot = fixtureRoot.toAbsolutePath().normalize();
        Path path = normalizedRoot.resolve(relativeFile).normalize();
        ensureInside(normalizedRoot, path, "JSON fixture must remain inside " + normalizedRoot);
        if (!path.getFileName().toString().toLowerCase().endsWith(".json")) {
            throw new IllegalArgumentException("JSON fixture must use a .json extension: " + relativeFile);
        }
        if (!Files.isRegularFile(path)) throw new WorkspaceException("JSON fixture does not exist: " + path);
        try {
            Path realWorkspace = root.toRealPath();
            Path realFixtureRoot = normalizedRoot.toRealPath();
            Path realPath = path.toRealPath();
            if (!realFixtureRoot.startsWith(realWorkspace) || !realPath.startsWith(realFixtureRoot)) {
                throw new IllegalArgumentException("JSON fixture symbolic link must remain inside workspace");
            }
            return realPath;
        } catch (IOException error) {
            throw new WorkspaceException("Cannot resolve JSON fixture: " + path, error);
        }
    }

    private void writeAtomically(Path target, byte[] content) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            try {
                Files.write(temporary, content);
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException error) {
            throw new WorkspaceException("Cannot write workspace file: " + target, error);
        }
    }

    private void ensureInside(Path parent, Path child, String message) {
        if (!child.startsWith(parent)) throw new IllegalArgumentException(message);
    }

    private record SecretDocument(Map<String, Map<String, String>> secrets) {}
}
