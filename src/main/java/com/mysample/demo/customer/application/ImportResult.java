package com.mysample.demo.customer.application;

import com.mysample.demo.customer.domain.Customer;

public record ImportResult(Customer customer, boolean created) {
}

