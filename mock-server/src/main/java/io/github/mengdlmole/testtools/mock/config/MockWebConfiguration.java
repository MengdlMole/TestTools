package io.github.mengdlmole.testtools.mock.config;

import io.github.mengdlmole.testtools.mock.callback.CallbackCompletionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
final class MockWebConfiguration implements WebMvcConfigurer {
  private final CallbackCompletionInterceptor callbackInterceptor;

  MockWebConfiguration(CallbackCompletionInterceptor callbackInterceptor) {
    this.callbackInterceptor = callbackInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(callbackInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns("/__testtools/**");
  }
}
