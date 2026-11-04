package com.mysample.demo.account.infrastructure.persistence;


import com.mysample.demo.account.domain.Account;
import com.mysample.demo.account.domain.port.AccountRepositoryPort;
import com.mysample.demo.account.domain.exception.AccountAlreadyExistsException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Driven adapter: stores accounts in PostgreSQL through Spring Data JPA. */
@Component
@RequiredArgsConstructor
class AccountPersistenceAdapter implements AccountRepositoryPort {

    /** Partial unique index from V2__create_account.sql: one ACTIVE account per customer and currency. */
    static final String ACTIVE_CURRENCY_CONSTRAINT = "uq_account_customer_currency_active";

    private final AccountJpaRepository repository;

    @Override
    public Account save(Account account) {
        try {
            // Flush now, so constraint and version conflicts surface here, not at commit time
            return toDomain(repository.saveAndFlush(toEntity(account)));
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent "open USD account" calls: the database decides, the loser gets a 409
            if (violates(ex, ACTIVE_CURRENCY_CONSTRAINT)) {
                throw new AccountAlreadyExistsException(account.customerId(), account.currency());
            }
            throw ex;
        }
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        return repository.findById(accountId).map(AccountPersistenceAdapter::toDomain);
    }

    @Override
    public List<Account> findByCustomerId(UUID customerId) {
        return repository.findByCustomerIdOrderByCreatedAtAsc(customerId).stream()
                .map(AccountPersistenceAdapter::toDomain)
                .toList();
    }

    private static boolean violates(DataIntegrityViolationException ex, String constraint) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraint);
    }

    private static AccountEntity toEntity(Account account) {
        AccountEntity entity = new AccountEntity();
        entity.setId(account.id());
        entity.setCustomerId(account.customerId());
        entity.setCurrency(account.currency());
        entity.setBalance(account.balance());
        entity.setStatus(account.status());
        entity.setVersion(account.version());
        entity.setCreatedAt(account.createdAt());
        return entity;
    }

    private static Account toDomain(AccountEntity entity) {
        return new Account(entity.getId(), entity.getCustomerId(), entity.getCurrency(),
                entity.getBalance(), entity.getStatus(), entity.getVersion(), entity.getCreatedAt());
    }
}
