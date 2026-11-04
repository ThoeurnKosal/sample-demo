package com.mysample.demo.shared.observability;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HttpLoggingProperties.class)
class HttpLoggingConfig {

    /**
     * Spring Security's filter chain is registered at -100: running just before it means
     * 401/403 responses are logged too. Boot's tracing filter runs earlier still, so the
     * traceId is already in the MDC when this filter writes its line.
     */
    static final int ORDER = -101;

    @Bean
    FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilter(HttpLoggingProperties properties) {
        SensitiveDataMasker masker = new SensitiveDataMasker(
                properties.maskedHeaders(), properties.maskedFields(), properties.partiallyMaskedFields());
        FilterRegistrationBean<HttpLoggingFilter> registration =
                new FilterRegistrationBean<>(new HttpLoggingFilter(properties, masker));
        registration.setName("httpLoggingFilter");
        registration.setOrder(ORDER);
        registration.setUrlPatterns(properties.urlPatterns());
        return registration;
    }
}
