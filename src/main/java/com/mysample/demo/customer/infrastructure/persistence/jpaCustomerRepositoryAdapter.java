package com.mysample.demo.customer.infrastructure.persistence;

import com.mysample.demo.customer.domain.Customer;
import com.mysample.demo.customer.domain.exception.DuplicateCustomerException;
import com.mysample.demo.customer.domain.port.CustomerRepository;
import com.mysample.demo.shared.pagging.PageResult;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class jpaCustomerRepositoryAdapter implements CustomerRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final CustomerJpaRepository jpa;
    private final CustomerEntityMapper mapper;

    @Override
    public Customer save(Customer customer) {
        try {
            jpa.saveAndFlush(mapper.toEntity(customer));
            return customer;
        } catch (DataIntegrityViolationException e) {
            // Unique constraint on email or external_id: someone got there first.
            throw new DuplicateCustomerException();
        }
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Customer> findByExternalId(int externalId) {
        return jpa.findByExternalId(externalId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public PageResult<Customer> findAll(int page, int size) {
        Page<CustomerEntity> result = jpa.findAll(PageRequest.of(page, size, NEWEST_FIRST));
        return new PageResult<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                page, size, result.getTotalElements());
    }
}
