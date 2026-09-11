package io.github.mengdlmole.testtools.security;

public class NoSecurityHandler implements HttpSecurityHandler {
    @Override public String id() { return "none"; }
}
