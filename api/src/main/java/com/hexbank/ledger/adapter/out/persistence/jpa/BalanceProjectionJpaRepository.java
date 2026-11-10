package com.hexbank.ledger.adapter.out.persistence.jpa;

import com.hexbank.ledger.adapter.out.persistence.entity.BalanceProjectionJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BalanceProjectionJpaRepository extends JpaRepository<BalanceProjectionJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BalanceProjectionJpaEntity p where p.ledgerAccountId in :ids order by p.ledgerAccountId")
    List<BalanceProjectionJpaEntity> lockAllOrderById(@Param("ids") Collection<UUID> ids);
}
