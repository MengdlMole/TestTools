package io.github.localtools.testtools.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.github.localtools.testtools.http.HttpModels.ResponseSnapshot;
import io.github.localtools.testtools.runner.RunModels.AssertionResult;
import io.github.localtools.testtools.workspace.WorkspaceModels.AssertionDefinition;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AssertionEngine {
    private final ObjectMapper mapper;

    public AssertionEngine(ObjectMapper mapper) { this.mapper = mapper; }

    public AssertionResult evaluate(AssertionDefinition definition, ResponseSnapshot response) {
        try {
            return switch (definition.type()) {
                case "status" -> compare("status", response.status(), value(definition.expected()), definition.operator());
                case "responseTime" -> compare("responseTime", response.durationMs(), value(definition.expected()), definition.operator());
                case "header" -> compare("header " + definition.path(), response.firstHeader(definition.path()),
                        value(definition.expected()), definition.operator());
                case "jsonPath" -> {
                    Object actual = JsonPath.read(response.bodyText(), definition.path());
                    yield compare("jsonPath " + definition.path(), actual, value(definition.expected()), definition.operator());
                }
                default -> new AssertionResult(definition.type(), false, "Unknown assertion type");
            };
        } catch (Exception e) {
            return new AssertionResult(definition.type(), false, e.getMessage());
        }
    }

    private AssertionResult compare(String label, Object actual, Object expected, String operatorValue) {
        String operator = operatorValue == null ? "equals" : operatorValue;
        boolean success = switch (operator) {
            case "equals" -> Objects.equals(normalize(actual), normalize(expected));
            case "notNull" -> actual != null;
            case "contains" -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
            case "greaterThan" -> number(actual) > number(expected);
            case "lessThan" -> number(actual) < number(expected);
            default -> false;
        };
        return new AssertionResult(label, success,
                success ? "passed" : "expected " + operator + " " + expected + ", actual " + actual);
    }

    private Object value(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return mapper.convertValue(node, Object.class);
    }

    private Object normalize(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        return value;
    }

    private double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }
}
