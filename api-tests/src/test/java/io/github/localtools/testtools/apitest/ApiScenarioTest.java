package io.github.localtools.testtools.apitest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiScenarioTest {
    @Test
    void repeatsWholeFlowAndSharesValuesOnlyInsideOneIteration() {
        List<String> events = new ArrayList<>();

        new ApiScenario("flow")
                .repeat(2)
                .logger(message -> {})
                .beforeEach(context -> {
                    events.add("before-" + context.iteration());
                    context.put("value", "v" + context.iteration());
                })
                .step("read", context -> events.add(context.require("value", String.class)))
                .afterEach(context -> events.add("after-" + context.iteration()))
                .run();

        assertEquals(List.of("before-1", "v1", "after-1", "before-2", "v2", "after-2"), events);
    }

    @Test
    void alwaysRunsCleanupAndPropagatesStepFailure() {
        List<String> events = new ArrayList<>();
        ApiScenario scenario = new ApiScenario("failure")
                .logger(message -> {})
                .step("fail", context -> { throw new IllegalStateException("boom"); })
                .afterEach(context -> events.add("cleanup"));

        assertThrows(IllegalStateException.class, scenario::run);
        assertEquals(List.of("cleanup"), events);
    }
}
