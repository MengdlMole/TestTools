package io.github.localtools.testtools.mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

import static io.github.localtools.testtools.mock.MockRuntimeModels.CallbackTask;

@Component
class CallbackCompletionInterceptor implements HandlerInterceptor {
    private final HttpCallbackDispatcher dispatcher;

    CallbackCompletionInterceptor(HttpCallbackDispatcher dispatcher) { this.dispatcher = dispatcher; }

    @Override
    @SuppressWarnings("unchecked")
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        Object callbacks = request.getAttribute(HttpMockController.CALLBACKS_ATTRIBUTE);
        if (callbacks instanceof List<?> tasks) {
            tasks.stream().filter(CallbackTask.class::isInstance).map(CallbackTask.class::cast)
                    .forEach(dispatcher::submit);
        }
    }
}
