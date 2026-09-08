package io.github.localtools.testtools.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.http.HttpModels.ResponseSnapshot;
import io.github.localtools.testtools.security.HttpSecurityHandler;
import io.github.localtools.testtools.security.SignContext;
import io.github.localtools.testtools.security.VerificationResult;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Lightweight code-first HTTP test DSL. It has no dependency on JUnit or TestNG. */
public final class ApiTestClient {
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;
    private final Duration defaultTimeout;
    private final ObjectMapper mapper;
    private final HttpExecutor executor;
    private final Consumer<String> logger;
    private final int bodyLogLimit;

    private ApiTestClient(Builder builder) {
        this.baseUrl = stripTrailingSlash(builder.baseUrl);
        this.defaultHeaders = Map.copyOf(builder.defaultHeaders);
        this.defaultTimeout = builder.timeout;
        this.mapper = builder.mapper;
        this.executor = builder.executor;
        this.logger = builder.logger;
        this.bodyLogLimit = builder.bodyLogLimit;
    }

    public static Builder builder(String baseUrl) { return new Builder(baseUrl); }
    public Request request(String method, String pathOrUrl) { return new Request(method, pathOrUrl); }
    public Request get(String pathOrUrl) { return request("GET", pathOrUrl); }
    public Request post(String pathOrUrl) { return request("POST", pathOrUrl); }
    public Request put(String pathOrUrl) { return request("PUT", pathOrUrl); }
    public Request patch(String pathOrUrl) { return request("PATCH", pathOrUrl); }
    public Request delete(String pathOrUrl) { return request("DELETE", pathOrUrl); }

    public final class Request {
        private final String method;
        private final String pathOrUrl;
        private final List<QueryParameter> query = new ArrayList<>();
        private final Map<String, String> headers = new LinkedHashMap<>(defaultHeaders);
        private final List<RequestSigner> signers = new ArrayList<>();
        private byte[] body = new byte[0];
        private Duration timeout = defaultTimeout;
        private ResponseVerifier responseVerifier = (request, response) -> VerificationResult.ok();

        private Request(String method, String pathOrUrl) {
            if (method == null || method.isBlank()) throw new IllegalArgumentException("HTTP method is required");
            if (pathOrUrl == null || pathOrUrl.isBlank()) throw new IllegalArgumentException("Path or URL is required");
            this.method = method.toUpperCase();
            this.pathOrUrl = pathOrUrl;
        }

        public Request query(String name, Object value) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Query name is required");
            query.add(new QueryParameter(name, value == null ? "" : String.valueOf(value)));
            return this;
        }

        public Request queries(Map<String, ?> values) {
            if (values != null) values.forEach(this::query);
            return this;
        }

        public Request header(String name, Object value) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Header name is required");
            headers.put(name, value == null ? "" : String.valueOf(value));
            return this;
        }

        public Request headers(Map<String, ?> values) {
            if (values != null) values.forEach(this::header);
            return this;
        }

        public Request body(String value) {
            body = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        public Request body(byte[] value) {
            body = value == null ? new byte[0] : value.clone();
            return this;
        }

        public Request bodyFile(Path file) {
            try { return body(Files.readAllBytes(file)); }
            catch (Exception error) { throw new IllegalArgumentException("Cannot read request body: " + file, error); }
        }

        public Request jsonBody(Object value) {
            try {
                body = value == null ? new byte[0] : mapper.writeValueAsBytes(value);
                if (body.length > 0) headers.putIfAbsent("Content-Type", "application/json");
                return this;
            } catch (Exception error) {
                throw new IllegalArgumentException("Cannot serialize JSON request body", error);
            }
        }

        /** Adds arbitrary request mutation/signing logic, executed after URI and body are final. */
        public Request signWith(RequestSigner signer) {
            signers.add(java.util.Objects.requireNonNull(signer));
            return this;
        }

        /** Uses a reusable handler for both request signing and response verification. */
        public Request security(HttpSecurityHandler handler, SignContext context) {
            signWith(request -> handler.signRequest(context, request));
            return verifyWith((request, response) -> handler.verifyResponse(context, request, response));
        }

        public Request verifyWith(ResponseVerifier verifier) {
            responseVerifier = java.util.Objects.requireNonNull(verifier);
            return this;
        }

        public Request timeout(Duration value) {
            timeout = java.util.Objects.requireNonNull(value);
            if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Timeout must be positive");
            return this;
        }

        public ApiResponse execute() {
            MutableRequest request = new MutableRequest(method, requestUri(pathOrUrl, query), headers, body);
            signers.forEach(signer -> signer.sign(request));

            log("--> " + request.method() + " " + SensitiveDataMasker.maskUri(request.uri()));
            if (!request.headers().isEmpty()) log("    request headers: " + SensitiveDataMasker.maskHeaders(request.headers()));
            if (request.body().length > 0) log("    request body   : " + bodyForLog(request.bodyText()));
            HttpExecutor.Exchange exchange = executor.execute(request, timeout);
            log("<-- HTTP " + exchange.response().status() + " (" + exchange.response().durationMs() + " ms)");
            if (!exchange.response().headers().isEmpty()) log("    response headers: "
                    + SensitiveDataMasker.maskHeaders(flatten(exchange.response().headers())));
            if (exchange.response().body().length > 0) log("    response body   : " + bodyForLog(exchange.response().bodyText()));
            VerificationResult verification = responseVerifier.verify(exchange.request(), exchange.response());
            log("    verification    : " + (verification.success() ? "PASSED" : "FAILED")
                    + " (" + verification.message() + ")");
            return new ApiResponse(exchange.request(), exchange.response(), verification, mapper);
        }

        public ApiResponse executeVerified() {
            ApiResponse response = execute();
            if (!response.verification().success()) {
                throw new ResponseVerificationException(response.verification().message());
            }
            return response;
        }
    }

    @FunctionalInterface
    public interface RequestSigner { void sign(MutableRequest request); }

    @FunctionalInterface
    public interface ResponseVerifier {
        VerificationResult verify(RequestSnapshot request, ResponseSnapshot response);
    }

    public static final class ResponseVerificationException extends AssertionError {
        public ResponseVerificationException(String message) {
            super(message == null || message.isBlank() ? "Response verification failed" : message);
        }
    }

    private record QueryParameter(String name, String value) {}

    public record ApiResponse(RequestSnapshot request, ResponseSnapshot response,
                              VerificationResult verification, ObjectMapper mapper) {
        public int status() { return response.status(); }
        public long durationMs() { return response.durationMs(); }
        public String body() { return response.bodyText(); }
        public String header(String name) { return response.firstHeader(name); }
        public JsonNode json() {
            try { return mapper.readTree(response.body()); }
            catch (Exception error) { throw new IllegalArgumentException("Response is not valid JSON", error); }
        }
        public Object jsonPath(String path) { return JsonPath.read(body(), path); }
    }

    public static final class Builder {
        private final String baseUrl;
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private Duration timeout = Duration.ofSeconds(30);
        private ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        private HttpExecutor executor = new HttpExecutor();
        private Consumer<String> logger = System.out::println;
        private int bodyLogLimit = Integer.getInteger("testtools.logBodyLimit", 4000);

        private Builder(String baseUrl) {
            if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("Base URL is required");
            URI parsed = URI.create(baseUrl.trim());
            if (!("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme()))
                    || parsed.getHost() == null) {
                throw new IllegalArgumentException("Base URL must be an absolute HTTP(S) URL: " + baseUrl);
            }
            this.baseUrl = baseUrl;
        }
        public Builder defaultHeader(String name, String value) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Header name is required");
            defaultHeaders.put(name, value == null ? "" : value);
            return this;
        }
        public Builder timeout(Duration value) {
            timeout = java.util.Objects.requireNonNull(value);
            if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Timeout must be positive");
            return this;
        }
        public Builder objectMapper(ObjectMapper value) { mapper = java.util.Objects.requireNonNull(value); return this; }
        public Builder executor(HttpExecutor value) { executor = java.util.Objects.requireNonNull(value); return this; }
        public Builder logger(Consumer<String> value) { logger = java.util.Objects.requireNonNull(value); return this; }
        public Builder bodyLogLimit(int value) {
            if (value < -1) throw new IllegalArgumentException("Body log limit must be -1 or greater");
            bodyLogLimit = value;
            return this;
        }
        public ApiTestClient build() { return new ApiTestClient(this); }
    }

    private URI requestUri(String pathOrUrl, List<QueryParameter> query) {
        String url = pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")
                ? pathOrUrl : baseUrl + (pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl);
        if (!query.isEmpty()) {
            int fragmentIndex = url.indexOf('#');
            String fragment = fragmentIndex < 0 ? "" : url.substring(fragmentIndex);
            String target = fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);
            String separator = target.contains("?") ? "&" : "?";
            String queryString = query.stream()
                    .map(entry -> encode(entry.name()) + "=" + encode(entry.value()))
                    .collect(java.util.stream.Collectors.joining("&"));
            url = target + separator + queryString + fragment;
        }
        return URI.create(url);
    }

    private String bodyForLog(String value) {
        if (bodyLogLimit < 0 || value.length() <= bodyLogLimit) return value;
        return value.substring(0, bodyLogLimit) + "... [truncated, total " + value.length() + " chars]";
    }
    private void log(String value) { logger.accept(value); }
    private static String stripTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static Map<String, String> flatten(Map<String, List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((key, value) -> result.put(key, String.join(", ", value)));
        return result;
    }
}
