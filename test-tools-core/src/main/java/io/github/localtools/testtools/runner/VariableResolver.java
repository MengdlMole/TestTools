package io.github.localtools.testtools.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableResolver {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([a-zA-Z0-9_.-]+)}");
    private final ObjectMapper mapper;

    public VariableResolver(ObjectMapper mapper) { this.mapper = mapper; }

    public String resolve(String input, Map<String, String> variables) {
        if (input == null) return null;
        Matcher matcher = VARIABLE.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) throw new IllegalArgumentException("Unknown variable: " + key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public JsonNode resolve(JsonNode input, Map<String, String> variables) {
        if (input == null) return null;
        if (input.isTextual()) return mapper.getNodeFactory().textNode(resolve(input.textValue(), variables));
        if (input.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            input.properties().forEach(entry -> result.set(entry.getKey(), resolve(entry.getValue(), variables)));
            return result;
        }
        if (input.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            input.forEach(item -> result.add(resolve(item, variables)));
            return result;
        }
        return input.deepCopy();
    }
}
