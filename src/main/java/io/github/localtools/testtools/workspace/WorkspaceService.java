package io.github.localtools.testtools.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.localtools.testtools.config.TestToolsProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static io.github.localtools.testtools.workspace.WorkspaceModels.*;

@Service
public class WorkspaceService {
    private final Path root;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public WorkspaceService(TestToolsProperties properties, ObjectMapper jsonMapper) {
        this.root = properties.workspace().toAbsolutePath().normalize();
        this.yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.jsonMapper = jsonMapper;
    }

    public Path root() { return root; }
    public WorkspaceConfig config() { return readYaml(root.resolve("workspace.yaml"), WorkspaceConfig.class); }

    public EnvironmentConfig environment(String name) {
        return readYaml(root.resolve("environments").resolve(safeName(name) + ".yaml"), EnvironmentConfig.class);
    }

    public TestCase testCase(String name) {
        return readYaml(root.resolve("cases").resolve(safeName(name) + ".yaml"), TestCase.class);
    }

    public List<String> caseNames() { return yamlNames(root.resolve("cases")); }
    public List<String> suiteNames() { return yamlNames(root.resolve("suites")); }

    public TestSuite testSuite(String name) {
        return readYaml(root.resolve("suites").resolve(safeName(name) + ".yaml"), TestSuite.class);
    }

    public String caseYaml(String name) {
        return readText(root.resolve("cases").resolve(safeName(name) + ".yaml"));
    }

    public synchronized TestCase saveCaseYaml(String name, String yaml) {
        if (yaml == null || yaml.isBlank()) throw new IllegalArgumentException("Case YAML must not be empty");
        try {
            TestCase parsed = yamlMapper.readValue(yaml, TestCase.class);
            if (parsed.name() == null || parsed.name().isBlank()) throw new IllegalArgumentException("Case name is required");
            if (parsed.steps() == null || parsed.steps().isEmpty()) throw new IllegalArgumentException("At least one step is required");
            writeAtomically(root.resolve("cases").resolve(safeName(name) + ".yaml"), yaml.getBytes(StandardCharsets.UTF_8));
            return parsed;
        } catch (IOException e) {
            throw new WorkspaceException("Invalid case YAML", e);
        }
    }

    public String suiteYaml(String name) {
        return readText(root.resolve("suites").resolve(safeName(name) + ".yaml"));
    }

    public synchronized TestSuite saveSuiteYaml(String name, String yaml) {
        if (yaml == null || yaml.isBlank()) throw new IllegalArgumentException("Suite YAML must not be empty");
        try {
            TestSuite parsed = yamlMapper.readValue(yaml, TestSuite.class);
            if (parsed.name() == null || parsed.name().isBlank()) throw new IllegalArgumentException("Suite name is required");
            if (parsed.cases() == null || parsed.cases().isEmpty()) throw new IllegalArgumentException("At least one case is required");
            writeAtomically(root.resolve("suites").resolve(safeName(name) + ".yaml"), yaml.getBytes(StandardCharsets.UTF_8));
            return parsed;
        } catch (IOException e) {
            throw new WorkspaceException("Invalid suite YAML", e);
        }
    }

    public List<MockDefinition> mocks() {
        return yamlNames(root.resolve("mocks")).stream()
                .map(name -> readYaml(root.resolve("mocks").resolve(name + ".yaml"), MockDefinition.class))
                .sorted(Comparator.comparing(mock -> mock.priority() == null ? 100 : mock.priority()))
                .toList();
    }

    public Map<String, String> secrets(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) return Map.of();
        Path path = root.resolve("secrets/local-secrets.yaml");
        if (!Files.exists(path)) return Map.of();
        SecretFile file = readYaml(path, SecretFile.class);
        return file.secrets() == null ? Map.of() : file.secrets().getOrDefault(secretRef, Map.of());
    }

    public byte[] bodyFile(String relativeFile) {
        try {
            Path path = root.resolve(relativeFile).normalize();
            if (!path.startsWith(root)) throw new IllegalArgumentException("Body file must remain inside workspace");
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new WorkspaceException("Cannot read body file: " + relativeFile, e);
        }
    }

    public ObjectMapper jsonMapper() { return jsonMapper; }

    public synchronized Path saveResult(String relativePath, Object result) {
        try {
            Path resultRoot = root.resolve("results").normalize();
            Path target = resultRoot.resolve(relativePath).normalize();
            if (!target.startsWith(resultRoot)) throw new IllegalArgumentException("Invalid result path");
            writeAtomically(target, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result));
            return target;
        } catch (IOException e) {
            throw new WorkspaceException("Cannot save result", e);
        }
    }

    public List<String> resultFiles(int limit) {
        Path resultRoot = root.resolve("results");
        if (!Files.isDirectory(resultRoot)) return List.of();
        try (var files = Files.walk(resultRoot)) {
            return files.filter(Files::isRegularFile)
                    .map(resultRoot::relativize)
                    .map(Path::toString)
                    .sorted(Comparator.reverseOrder())
                    .limit(Math.max(1, Math.min(limit, 500)))
                    .toList();
        } catch (IOException e) {
            throw new WorkspaceException("Cannot list results", e);
        }
    }

    public JsonNode result(String relativePath) {
        try {
            Path resultRoot = root.resolve("results").normalize();
            Path target = resultRoot.resolve(relativePath).normalize();
            if (!target.startsWith(resultRoot)) throw new IllegalArgumentException("Invalid result path");
            return jsonMapper.readTree(target.toFile());
        } catch (IOException e) {
            throw new WorkspaceException("Cannot read result: " + relativePath, e);
        }
    }

    private <T> T readYaml(Path path, Class<T> type) {
        try {
            return yamlMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new WorkspaceException("Cannot read YAML: " + path, e);
        }
    }

    private String readText(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new WorkspaceException("Cannot read file: " + path, e);
        }
    }

    private void writeAtomically(Path target, byte[] content) throws IOException {
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
    }

    private List<String> yamlNames(Path directory) {
        if (!Files.isDirectory(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yaml") || name.endsWith(".yml"))
                    .map(name -> name.substring(0, name.lastIndexOf('.')))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new WorkspaceException("Cannot list YAML directory: " + directory, e);
        }
    }

    private String safeName(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid file name");
        }
        return name;
    }
}
