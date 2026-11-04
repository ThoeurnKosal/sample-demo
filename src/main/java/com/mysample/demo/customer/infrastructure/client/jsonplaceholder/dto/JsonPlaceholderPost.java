package com.mysample.demo.customer.infrastructure.client.jsonplaceholder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonPlaceholderPost(int id, int userId, String title, String body) {
}
