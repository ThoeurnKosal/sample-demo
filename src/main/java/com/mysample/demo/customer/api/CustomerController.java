package com.mysample.demo.customer.api;

import com.mysample.demo.customer.api.dto.CreateCustomerRequest;
import com.mysample.demo.customer.api.dto.CustomerPostResponse;
import com.mysample.demo.customer.api.dto.CustomerResponse;
import com.mysample.demo.customer.api.dto.ImportCustomerRequest;
import com.mysample.demo.customer.api.dto.PageResponse;
import com.mysample.demo.customer.application.CustomerService;
import com.mysample.demo.customer.application.ImportResult;
import com.mysample.demo.customer.domain.Customer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerApiMapper mapper;


    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_customers:write')")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = customerService.register(mapper.toDomain(request));
        return ResponseEntity.created(locationOf(customer)).body(mapper.toResponse(customer));
    }

    @PostMapping("/imports")
    @PreAuthorize("hasAuthority('SCOPE_customers:write')")
    public ResponseEntity<CustomerResponse> importFromDirectory(@Valid @RequestBody ImportCustomerRequest request) {
        ImportResult result = customerService.importFromDirectory(request.externalUserId());
        CustomerResponse body = mapper.toResponse(result.customer());
        return result.created()
                ? ResponseEntity.created(locationOf(result.customer())).body(body)
                : ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_customers:read')")
    public CustomerResponse get(@PathVariable UUID id){
        return mapper.toResponse(customerService.get(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_customers:read')")
    public PageResponse<CustomerResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(20)  @Max(100) int size
    ){
        return mapper.toPageResponse(customerService.list(page, size));
    }

    @GetMapping("/{id}/posts")
    @PreAuthorize("hasAuthority('SCOPE_customers:read')")
    public List<CustomerPostResponse> posts(@PathVariable UUID id) {
        return mapper.toPostResponses(customerService.postsOf(id));
    }

    private static URI locationOf(Customer customer) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/customers/{id}")
                .buildAndExpand(customer.id())
                .toUri();
    }

}
