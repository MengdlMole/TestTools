package io.github.localtools.testtools.security;

import org.springframework.stereotype.Component;

@Component
public class NoSecurityHandler implements ApiSecurityHandler {
    @Override public String id() { return "none"; }
}
