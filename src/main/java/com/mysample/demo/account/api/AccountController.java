package com.mysample.demo.account.api;

import com.mysample.demo.account.api.dto.AccountResponse;
import com.mysample.demo.account.api.dto.AmountRequest;
import com.mysample.demo.account.api.dto.OpenAccountRequest;
import com.mysample.demo.account.application.AccountService;
import com.mysample.demo.account.domain.Account;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountApiMapper mapper;

    @PostMapping("/customers/{customerId}/accounts")
    @PreAuthorize("hasAuthority('SCOPE_accounts:write')")
    public ResponseEntity<AccountResponse> open(@PathVariable UUID customerId,
                                                @Valid @RequestBody OpenAccountRequest request) {
        Account account = accountService.open(customerId, request.currency());
        return ResponseEntity.created(locationOf(account)).body(mapper.toResponse(account));
    }

    /** Not paged: one ACTIVE account per currency keeps the list short. */
    @GetMapping("/customers/{customerId}/accounts")
    @PreAuthorize("hasAuthority('SCOPE_accounts:read')")
    public List<AccountResponse> accountsOf(@PathVariable UUID customerId) {
        return mapper.toResponses(accountService.accountsOf(customerId));
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize("hasAuthority('SCOPE_accounts:read')")
    public AccountResponse get(@PathVariable UUID accountId) {
        return mapper.toResponse(accountService.get(accountId));
    }

    @PostMapping("/accounts/{accountId}/deposits")
    @PreAuthorize("hasAuthority('SCOPE_accounts:write')")
    public AccountResponse deposit(@PathVariable UUID accountId, @Valid @RequestBody AmountRequest request) {
        return mapper.toResponse(accountService.deposit(accountId, request.amount()));
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    @PreAuthorize("hasAuthority('SCOPE_accounts:write')")
    public AccountResponse withdraw(@PathVariable UUID accountId, @Valid @RequestBody AmountRequest request) {
        return mapper.toResponse(accountService.withdraw(accountId, request.amount()));
    }

    @PostMapping("/accounts/{accountId}/close")
    @PreAuthorize("hasAuthority('SCOPE_accounts:write')")
    public AccountResponse close(@PathVariable UUID accountId) {
        return mapper.toResponse(accountService.close(accountId));
    }

    private static URI locationOf(Account account) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/accounts/{id}")
                .buildAndExpand(account.id())
                .toUri();
    }
}
