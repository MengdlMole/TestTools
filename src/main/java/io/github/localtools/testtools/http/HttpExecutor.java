package io.github.localtools.testtools.http;

import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.http.HttpModels.ResponseSnapshot;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class HttpExecutor {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Exchange execute(MutableRequest request) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri()).timeout(Duration.ofSeconds(30));
            request.headers().forEach(builder::header);
            HttpRequest.BodyPublisher body = request.body().length == 0
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(request.body());
            builder.method(request.method(), body);
            long started = System.nanoTime();
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            long duration = Duration.ofNanos(System.nanoTime() - started).toMillis();
            Map<String, List<String>> requestHeaders = request.headers().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue())));
            RequestSnapshot requestSnapshot = new RequestSnapshot(request.method(), request.uri(), requestHeaders, request.body());
            ResponseSnapshot responseSnapshot = new ResponseSnapshot(
                    response.statusCode(), response.headers().map(), response.body(), duration);
            return new Exchange(requestSnapshot, responseSnapshot);
        } catch (Exception e) {
            throw new HttpExecutionException("Request failed: " + request.method() + " " + request.uri(), e);
        }
    }

    public record Exchange(RequestSnapshot request, ResponseSnapshot response) {}

    public static class HttpExecutionException extends RuntimeException {
        public HttpExecutionException(String message, Throwable cause) { super(message, cause); }
    }
}
