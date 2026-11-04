package com.mysample.demo.account.api;

import com.mysample.demo.account.api.dto.AccountResponse;
import com.mysample.demo.account.domain.Account;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Domain to HTTP. Field names match one to one; {@code status} (enum) becomes its name.
 * A response field without a source fails the build instead of being silently null.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
interface AccountApiMapper {

    AccountResponse toResponse(Account account);

    List<AccountResponse> toResponses(List<Account> accounts);
}
