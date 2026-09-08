package io.github.localtools.testtools.runnercli;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Ordered, repeatable API scenario for related code-first test steps. */
public final class ApiScenario {
    private final String name;
    private final List<Step> steps = new ArrayList<>();
    private Consumer<Context> beforeEach = context -> {};
    private Consumer<Context> afterEach = context -> {};
    private Consumer<String> logger = System.out::println;
    private int repeat = 1;

    ApiScenario(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Scenario name is required");
        this.name = name;
    }

    public ApiScenario repeat(int value) {
        if (value < 1 || value > 100) throw new IllegalArgumentException("Scenario repeat must be between 1 and 100");
        repeat = value;
        return this;
    }

    public ApiScenario beforeEach(Consumer<Context> action) {
        beforeEach = Objects.requireNonNull(action);
        return this;
    }

    public ApiScenario step(String stepName, Consumer<Context> action) {
        if (stepName == null || stepName.isBlank()) throw new IllegalArgumentException("Step name is required");
        steps.add(new Step(stepName, Objects.requireNonNull(action)));
        return this;
    }

    public ApiScenario afterEach(Consumer<Context> action) {
        afterEach = Objects.requireNonNull(action);
        return this;
    }

    public ApiScenario logger(Consumer<String> value) {
        logger = Objects.requireNonNull(value);
        return this;
    }

    public void run() {
        if (steps.isEmpty()) throw new IllegalStateException("Scenario must contain at least one step");
        for (int iteration = 1; iteration <= repeat; iteration++) {
            Context context = new Context(iteration, repeat);
            long scenarioStarted = System.nanoTime();
            Throwable activeFailure = null;
            logger.accept("\n========== SCENARIO START: " + name + " [" + iteration + "/" + repeat + "] ==========");
            try {
                beforeEach.accept(context);
                for (Step step : steps) runStep(step, context);
            } catch (RuntimeException | Error error) {
                activeFailure = error;
                throw error;
            } finally {
                try {
                    afterEach.accept(context);
                } catch (RuntimeException | Error cleanupError) {
                    if (activeFailure == null) throw cleanupError;
                    activeFailure.addSuppressed(cleanupError);
                } finally {
                    long duration = Duration.ofNanos(System.nanoTime() - scenarioStarted).toMillis();
                    logger.accept("========== SCENARIO END: " + name + " [" + iteration + "/" + repeat
                            + "], " + duration + " ms ==========\n");
                }
            }
        }
    }

    private void runStep(Step step, Context context) {
        long started = System.nanoTime();
        logger.accept("[scenario step] START: " + step.name());
        try {
            step.action().accept(context);
            long duration = Duration.ofNanos(System.nanoTime() - started).toMillis();
            logger.accept("[scenario step] PASSED: " + step.name() + " (" + duration + " ms)");
        } catch (RuntimeException | Error error) {
            long duration = Duration.ofNanos(System.nanoTime() - started).toMillis();
            logger.accept("[scenario step] FAILED: " + step.name() + " (" + duration + " ms): " + error);
            throw error;
        }
    }

    private record Step(String name, Consumer<Context> action) {}

    public static final class Context {
        private final int iteration;
        private final int totalIterations;
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Context(int iteration, int totalIterations) {
            this.iteration = iteration;
            this.totalIterations = totalIterations;
        }

        public int iteration() { return iteration; }
        public int totalIterations() { return totalIterations; }
        public Context put(String name, Object value) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Scenario value name is required");
            values.put(name, Objects.requireNonNull(value, "Scenario value is required"));
            return this;
        }
        public Object get(String name) { return values.get(name); }
        public <T> T require(String name, Class<T> type) {
            Object value = values.get(name);
            if (value == null) throw new IllegalStateException("Missing scenario value: " + name);
            return type.cast(value);
        }
        public Map<String, Object> values() { return Map.copyOf(values); }
    }
}
