package io.github.localtools.testtools.web;

import io.github.localtools.testtools.mock.LocalMockServer;
import io.github.localtools.testtools.runner.AutomationRunner;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.workspace.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final WorkspaceService workspace;
    private final AutomationRunner runner;
    private final SecurityHandlerRegistry handlers;
    private final LocalMockServer mockServer;

    public ApiController(WorkspaceService workspace, AutomationRunner runner,
                         SecurityHandlerRegistry handlers, LocalMockServer mockServer) {
        this.workspace = workspace;
        this.runner = runner;
        this.handlers = handlers;
        this.mockServer = mockServer;
    }

    @GetMapping("/workspace")
    public Object workspace() {
        return Map.of(
                "root", workspace.root().toString(),
                "cases", workspace.caseNames(),
                "suites", workspace.suiteNames(),
                "securityHandlers", handlers.ids(),
                "mockPort", mockServer.port());
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return Map.of("workspace", workspace.root().toString(), "cases", workspace.caseNames(),
                "securityHandlers", handlers.ids(), "mocks", workspace.mocks().size(),
                "mockUrl", "http://127.0.0.1:" + mockServer.port());
    }

    @PostMapping("/cases/{name}/run")
    public Object run(@PathVariable String name) { return runner.runCase(name); }

    @PostMapping("/cases/run-all")
    public Object runAll() { return runner.runAll(); }

    @GetMapping(value = "/cases/{name}", produces = "text/yaml;charset=UTF-8")
    public String caseYaml(@PathVariable String name) { return workspace.caseYaml(name); }

    @PutMapping(value = "/cases/{name}", consumes = {"text/yaml", "text/plain"})
    public Object saveCase(@PathVariable String name, @RequestBody String yaml) {
        return workspace.saveCaseYaml(name, yaml);
    }

    @GetMapping(value = "/suites/{name}", produces = "text/yaml;charset=UTF-8")
    public String suiteYaml(@PathVariable String name) { return workspace.suiteYaml(name); }

    @PutMapping(value = "/suites/{name}", consumes = {"text/yaml", "text/plain"})
    public Object saveSuite(@PathVariable String name, @RequestBody String yaml) {
        return workspace.saveSuiteYaml(name, yaml);
    }

    @PostMapping("/suites/{name}/run")
    public Object runSuite(@PathVariable String name) { return runner.runSuite(name); }

    @GetMapping("/results")
    public Object results(@RequestParam(defaultValue = "100") int limit) {
        return workspace.resultFiles(limit);
    }

    @GetMapping("/result")
    public Object result(@RequestParam String path) { return workspace.result(path); }

    @GetMapping("/mocks/calls")
    public Object mockCalls() { return mockServer.recentCalls(); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> error(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
