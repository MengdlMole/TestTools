package io.github.localtools.testtools.security;

public class NoSecurityHandler implements HttpSecurityHandler {
    @Override public String id() { return "none"; }
}
