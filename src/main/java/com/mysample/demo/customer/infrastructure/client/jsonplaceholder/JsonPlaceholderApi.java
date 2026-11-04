package com.mysample.demo.customer.infrastructure.client.jsonplaceholder;


import com.mysample.demo.customer.infrastructure.client.jsonplaceholder.dto.JsonPlaceholderPost;
import com.mysample.demo.customer.infrastructure.client.jsonplaceholder.dto.JsonPlaceholderUser;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
public interface JsonPlaceholderApi {
    /** HTTP service group name; also the name of the Resilience4j instances. */
    String GROUP = "jsonplaceholder";

    @GetExchange("/users/{id}")
    JsonPlaceholderUser getUser(@PathVariable int id);

    @GetExchange("/posts")
    List<JsonPlaceholderPost> getPostsByUser(@RequestParam("userId") int userId);

}
