package com.mysample.demo.customer.infrastructure.client.jsonplaceholder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonPlaceholderUser(
        int id,
        String name,
        String email,
        String phone,
        Address address,
        Company company
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(String street,String city, String zipcode){}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Company(String name) {}
}
