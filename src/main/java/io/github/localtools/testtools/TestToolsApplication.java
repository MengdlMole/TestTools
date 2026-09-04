package io.github.localtools.testtools;

import io.github.localtools.testtools.config.TestToolsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TestToolsProperties.class)
public class TestToolsApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestToolsApplication.class, args);
    }
}
