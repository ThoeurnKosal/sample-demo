package com.mysample.demo.customer.infrastructure.client.jsonplaceholder;

import com.mysample.demo.shared.error.exception.DownstreamRejectedException;
import com.mysample.demo.shared.error.exception.DownstreamUnavailableException;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class JsonPlaceholderErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.value() == 404) {
            throw new JsonPlaceholderNotFoundException();
        }
        if (status.value() == 429 || status.is5xxServerError()) {
            throw new DownstreamUnavailableException(JsonPlaceholderApi.GROUP, status.value(), null);
        }
        throw new DownstreamRejectedException(JsonPlaceholderApi.GROUP, status.value());
    }


}
