package io.github.localtools.testtools.http;


import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class HttpExecutor {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Exchange execute(MutableRequest request) {
        return execute(request, Duration.ofSeconds(30));
    }

    public Exchange execute(MutableRequest request, Duration timeout) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri()).timeout(timeout);
            request.headers().forEach(builder::header);
            byte[] requestBody = request.body();
            HttpRequest.BodyPublisher body = requestBody.length == 0
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(requestBody);
            builder.method(request.method(), body);
            long started = System.nanoTime();
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            long duration = Duration.ofNanos(System.nanoTime() - started).toMillis();
            Map<String, List<String>> requestHeaders = request.headers().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue())));
            RequestSnapshot requestSnapshot = new RequestSnapshot(request.method(), request.uri(), requestHeaders, requestBody);
            ResponseSnapshot responseSnapshot = new ResponseSnapshot(
                    response.statusCode(), response.headers().map(), response.body(), duration);
            return new Exchange(requestSnapshot, responseSnapshot);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new HttpExecutionException("Request interrupted: " + request.method() + " "
                    + SensitiveDataMasker.maskUri(request.uri()), error);
        } catch (Exception e) {
            throw new HttpExecutionException("Request failed: " + request.method() + " "
                    + SensitiveDataMasker.maskUri(request.uri()), e);
        }
    }

    public record Exchange(RequestSnapshot request, ResponseSnapshot response) {}

    public static class HttpExecutionException extends RuntimeException {
        public HttpExecutionException(String message, Throwable cause) { super(message, cause); }
    }
}
