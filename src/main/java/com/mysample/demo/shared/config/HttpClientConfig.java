package com.mysample.demo.shared.config;

import com.mysample.demo.customer.infrastructure.client.jsonplaceholder.JsonPlaceholderApi;
import com.mysample.demo.customer.infrastructure.client.jsonplaceholder.JsonPlaceholderErrorHandler;
import com.mysample.demo.shared.observability.DownstreamLoggingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = JsonPlaceholderApi.GROUP, types = JsonPlaceholderApi.class)
public class HttpClientConfig {

    @Bean
    RestClientHttpServiceGroupConfigurer downstreamLoggingConfigurer() {
        return groups -> groups.forEachClient((group, builder) ->
                builder.requestInterceptor(new DownstreamLoggingInterceptor(group.name())));
    }

    @Bean
    RestClientHttpServiceGroupConfigurer jsonPlaceholderConfigurer(JsonPlaceholderErrorHandler errorHandler) {
        return groups -> groups.filterByName(JsonPlaceholderApi.GROUP)
                .forEachClient((group, builder) -> builder.defaultStatusHandler(HttpStatusCode::isError, errorHandler));
    }
}
