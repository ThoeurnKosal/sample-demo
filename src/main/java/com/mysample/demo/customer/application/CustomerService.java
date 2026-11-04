package com.mysample.demo.customer.application;

import com.mysample.demo.customer.domain.Customer;
import com.mysample.demo.customer.domain.CustomerDetails;
import com.mysample.demo.customer.domain.CustomerPost;
import com.mysample.demo.customer.domain.ExternalUser;
import com.mysample.demo.customer.domain.exception.CustomerNotFoundException;
import com.mysample.demo.customer.domain.exception.DuplicateCustomerException;
import com.mysample.demo.customer.domain.port.CustomerDirectoryPort;
import com.mysample.demo.customer.domain.port.CustomerRepository;
import com.mysample.demo.shared.pagging.PageResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customers;
    private final CustomerDirectoryPort directory;
    private final Clock clock;
    private final MeterRegistry meterRegistry;


    public Customer register(CustomerDetails details) {
        // Fast, friendly check; the unique constraint is what actually guarantees it.
        if (customers.existsByEmail(details.email())) {
            throw new DuplicateCustomerException();
        }
        return customers.save(Customer.register(UUID.randomUUID(), details, clock.instant()));
    }

    /**
     * Creates a customer from a user of the external directory. Idempotent: importing the
     * same user again returns the existing customer without calling the directory.
     */
    public ImportResult importFromDirectory(int externalId) {
        Optional<Customer> existing = customers.findByExternalId(externalId);
        if (existing.isPresent()) {
            countImport("already_imported");
            return new ImportResult(existing.get(), false);
        }

        ExternalUser user = directory.findUser(externalId);          // remote call, outside any transaction
        Customer customer = Customer.importFrom(UUID.randomUUID(), user, clock.instant());
        try {
            ImportResult result = new ImportResult(customers.save(customer), true);
            countImport("created");
            return result;
        } catch (DuplicateCustomerException e) {
            // A concurrent request imported the same user first: return that customer.
            // If it is not there, the conflict is the email, so the error stands.
            Customer winner = customers.findByExternalId(externalId).orElseThrow(() -> e);
            countImport("already_imported");
            return new ImportResult(winner, false);
        }
    }

    public Customer get(UUID id) {
        return customers.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    public PageResult<Customer> list(int page, int size) {
        return customers.findAll(page, size);
    }

    /** Posts from the directory. Customers registered directly (not imported) have none. */
    public List<CustomerPost> postsOf(UUID customerId) {
        Customer customer = get(customerId);
        if (!customer.isLinkedToDirectory()) {
            return List.of();
        }
        return directory.findPosts(customer.externalId());
    }

    private void countImport(String outcome) {
        meterRegistry.counter("customer.imports", "outcome", outcome).increment();
    }


}
