package com.mysample.demo.account.application;


import com.mysample.demo.account.domain.Account;
import com.mysample.demo.account.domain.port.AccountRepositoryPort;
import com.mysample.demo.account.domain.port.CustomerLookupPort;
import com.mysample.demo.account.domain.exception.AccountNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepositoryPort accounts;
    private final CustomerLookupPort customers;

    public Account open(UUID customerId, String currency) {
        customers.requireExists(customerId);
        Account account = accounts.save(Account.open(customerId, currency, Instant.now()));
        log.info("Account opened: account={} customer={} currency={}",
                account.id(), customerId, account.currency());
        return account;
    }

    @Transactional(readOnly = true)
    public Account get(UUID accountId) {
        return load(accountId);
    }

    @Transactional(readOnly = true)
    public List<Account> accountsOf(UUID customerId) {
        customers.requireExists(customerId);
        return accounts.findByCustomerId(customerId);
    }

    public Account deposit(UUID accountId, BigDecimal amount) {
        Account account = accounts.save(load(accountId).deposit(amount));
        log.info("Deposit: account={} amount={} {}", accountId, amount, account.currency());
        return account;
    }

    public Account withdraw(UUID accountId, BigDecimal amount) {
        Account account = accounts.save(load(accountId).withdraw(amount));
        log.info("Withdrawal: account={} amount={} {}", accountId, amount, account.currency());
        return account;
    }

    public Account close(UUID accountId) {
        Account account = accounts.save(load(accountId).close());
        log.info("Account closed: account={}", accountId);
        return account;
    }

    private Account load(UUID accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
