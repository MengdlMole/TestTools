package io.github.localtools.testtools.runnercli;

import org.testng.annotations.Test;

/**
 * IDE entry point: each Java test method maps to one YAML case and can be run or debugged alone.
 *
 * <p>Run this class with the TestNG runner in IDEA or VS Code. It deliberately does not match
 * Maven Surefire's default test class naming patterns, because the target service or mock server
 * is not guaranteed to be running during a normal unit-test build.</p>
 */
public final class IdeApiTestLauncher extends ApiCaseTestSupport {
    @Test(description = "Run cases/plain-health.yaml")
    public void plainHealth() {
        runCase("plain-health");
    }

    @Test(description = "Run cases/signed-echo.yaml")
    public void signedEcho() {
        runCase("signed-echo");
    }
}
