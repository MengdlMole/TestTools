package io.github.localtools.testtools.mock;

import io.github.localtools.testtools.http.HttpExecutor;
import io.github.localtools.testtools.workspace.VariableResolver;
import io.github.localtools.testtools.security.SecurityHandlerLoader;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.mock.config.MockWorkspace;
import io.github.localtools.testtools.workspace.TestWorkspace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class MockServerApplication {
    public static void main(String[] args) {
        Path workspacePath = argument(args, "--workspace", "test-workspace").toAbsolutePath().normalize();
        MockWorkspace workspace = new MockWorkspace(new TestWorkspace(workspacePath));
        int port = workspace.config().resolvedPort();

        SpringApplication application = new SpringApplication(MockServerApplication.class);
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("server.address", "127.0.0.1");
        defaults.put("server.port", port);
        defaults.put("spring.application.name", "local-mock-server");
        defaults.put("test-tools.workspace", workspacePath.toString());
        application.setDefaultProperties(defaults);
        application.run(withoutWorkspaceArgument(args));
    }

    @Bean
    TestWorkspace testWorkspace(@Value("${test-tools.workspace}") String path) {
        return new TestWorkspace(Path.of(path));
    }

    @Bean MockWorkspace mockWorkspace(TestWorkspace workspace) { return new MockWorkspace(workspace); }

    @Bean SecurityHandlerRegistry securityHandlerRegistry() { return SecurityHandlerLoader.create(); }
    @Bean VariableResolver variableResolver(TestWorkspace workspace) { return new VariableResolver(workspace.jsonMapper()); }
    @Bean HttpExecutor httpExecutor() { return new HttpExecutor(); }

    @Bean
    ThreadPoolTaskScheduler callbackTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("mock-callback-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }

    private static Path argument(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) return Path.of(args[i + 1]);
        }
        return Path.of(fallback);
    }

    private static String[] withoutWorkspaceArgument(String[] args) {
        List<String> filtered = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("--workspace".equals(args[i])) { i++; continue; }
            filtered.add(args[i]);
        }
        return filtered.toArray(String[]::new);
    }
}
