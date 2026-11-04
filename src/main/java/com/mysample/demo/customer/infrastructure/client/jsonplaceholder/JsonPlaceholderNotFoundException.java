package com.mysample.demo.customer.infrastructure.client.jsonplaceholder;

import com.mysample.demo.shared.error.exception.DownstreamRejectedException;
import java.io.Serial;

public class JsonPlaceholderNotFoundException extends DownstreamRejectedException {
    @Serial
    private static final long serialVersionUID = 1L;

    JsonPlaceholderNotFoundException() {
        super(JsonPlaceholderApi.GROUP, 404);
    }
}
