package com.hexbank.accounts.adapter.out.persistence.jpa;

import com.hexbank.accounts.adapter.out.persistence.entity.AccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {
}
