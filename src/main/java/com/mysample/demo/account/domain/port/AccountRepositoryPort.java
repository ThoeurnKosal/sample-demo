package com.mysample.demo.account.domain.port;

import com.mysample.demo.account.domain.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepositoryPort {

    Account save(Account account);

    Optional<Account> findById(UUID accountId);

    List<Account> findByCustomerId(UUID customerId);

}
