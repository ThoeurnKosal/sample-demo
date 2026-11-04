package com.mysample.demo.customer.domain.port;

import com.mysample.demo.customer.domain.Customer;
import com.mysample.demo.shared.pagging.PageResult;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByExternalId(int externalId);

    boolean existsByEmail(String email);

    PageResult<Customer> findAll(int page, int size);

}
