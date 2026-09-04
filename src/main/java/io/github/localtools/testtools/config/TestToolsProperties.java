package io.github.localtools.testtools.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "test-tools")
public record TestToolsProperties(Path workspace) {
}
