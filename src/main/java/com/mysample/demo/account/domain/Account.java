package com.mysample.demo.account.domain;

import com.mysample.demo.account.domain.exception.AccountBalanceNotZeroException;
import com.mysample.demo.account.domain.exception.AccountNotActiveException;
import com.mysample.demo.account.domain.exception.InsufficientFundsException;
import com.mysample.demo.account.domain.exception.InvalidAmountException;
import com.mysample.demo.account.domain.exception.UnsupportedCurrencyException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A customer's wallet in one currency. Immutable: every operation returns a new instance,
 * the caller saves it. All money rules live here, not in the service or the database alone.
 *
 * @param version {@code null} until the account is first saved (optimistic locking afterwards)
 */
public record Account(
        UUID id,
        UUID customerId,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        Long version,
        Instant createdAt) {

    public static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "KHR");

    /** Two decimals for every supported currency (ISO 4217 minor unit of USD and KHR). */
    public static final int MONEY_SCALE = 2;

    /** Largest value that fits the NUMERIC(19,2) column. */
    public static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (balance.signum() < 0) {
            throw new IllegalArgumentException("balance must not be negative");
        }
        balance = balance.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    /** Opens an empty, active account. The caller has checked that the customer exists. */
    public static Account open(UUID customerId, String currency, Instant now) {
        String code = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CURRENCIES.contains(code)) {
            throw new UnsupportedCurrencyException(currency);
        }
        return new Account(UUID.randomUUID(), customerId, code, BigDecimal.ZERO,
                AccountStatus.ACTIVE, null, now);
    }

    public Account deposit(BigDecimal amount) {
        requireActive();
        BigDecimal value = validAmount(amount);
        BigDecimal newBalance = balance.add(value);
        if (newBalance.compareTo(MAX_BALANCE) > 0) {
            throw new InvalidAmountException(amount, currency);
        }
        return withBalance(newBalance);
    }

    public Account withdraw(BigDecimal amount) {
        requireActive();
        BigDecimal value = validAmount(amount);
        if (balance.compareTo(value) < 0) {
            throw new InsufficientFundsException(id);
        }
        return withBalance(balance.subtract(value));
    }

    /** Only an empty account can be closed: money is never silently lost. */
    public Account close() {
        requireActive();
        if (balance.signum() != 0) {
            throw new AccountBalanceNotZeroException(id);
        }
        return new Account(id, customerId, currency, balance, AccountStatus.CLOSED, version, createdAt);
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(id);
        }
    }

    /** Strictly positive, at most two decimals. */
    private BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new InvalidAmountException(amount, currency);
        }
        return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private Account withBalance(BigDecimal newBalance) {
        return new Account(id, customerId, currency, newBalance, status, version, createdAt);
    }
}
