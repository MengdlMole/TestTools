package io.github.localtools.testtools.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
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
        try {
            return mapper.readTree(resolve(mapper.writeValueAsString(input), variables));
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot resolve JSON variables", e);
        }
    }
}
