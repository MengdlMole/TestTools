package io.github.mengdlmole.testtools.yamlrunner.result;

public record ErrorDetail(String type, String message) {
    public static ErrorDetail from(Throwable error) {
        return new ErrorDetail(error.getClass().getName(), error.getMessage());
    }
}
