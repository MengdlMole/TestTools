package io.github.mengdlmole.testtools.mock;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class MockWebConfiguration implements WebMvcConfigurer {
    private final CallbackCompletionInterceptor callbackInterceptor;

    MockWebConfiguration(CallbackCompletionInterceptor callbackInterceptor) {
        this.callbackInterceptor = callbackInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(callbackInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/__testtools/**");
    }
}
