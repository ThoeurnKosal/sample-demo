package com.mysample.demo.account.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

    List<AccountEntity> findByCustomerIdOrderByCreatedAtAsc(UUID customerId);

}
