package io.github.mengdlmole.testtools.mock.callback;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public final class CallbackCompletionInterceptor implements HandlerInterceptor {
  /**
   * Servlet request attribute used to defer callbacks until the Mock response completes.
   */
  public static final String CALLBACKS_ATTRIBUTE =
      CallbackCompletionInterceptor.class.getName() + ".callbacks";

  private final HttpCallbackDispatcher dispatcher;

  public CallbackCompletionInterceptor(HttpCallbackDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    Object callbacks = request.getAttribute(CALLBACKS_ATTRIBUTE);
    if (callbacks instanceof List<?> tasks) {
      tasks.stream()
          .filter(CallbackTask.class::isInstance)
          .map(CallbackTask.class::cast)
          .forEach(dispatcher::submit);
    }
  }
}
