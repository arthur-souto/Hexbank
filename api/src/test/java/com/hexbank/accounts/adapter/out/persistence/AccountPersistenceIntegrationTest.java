package com.hexbank.accounts.adapter.out.persistence;

import com.hexbank.TestContainersConfiguration;
import com.hexbank.accounts.adapter.out.persistence.entity.AccountJpaEntity;
import com.hexbank.accounts.adapter.out.persistence.jpa.AccountJpaRepository;
import com.hexbank.accounts.domain.Account;
import com.hexbank.accounts.domain.AccountId;
import com.hexbank.accounts.domain.AccountStatus;
import com.hexbank.bootstrap.ApiApplication;
import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.shared.domain.InstanceCurrency;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V2__accounts.sql + mapeamento JPA. Requer Docker (Testcontainers).
 * O próprio boot do contexto já valida a entidade contra a tabela (spring.jpa.hibernate.ddl-auto=validate).
 * Cada teste roda numa transação revertida ao final.
 */
@SpringBootTest(classes = ApiApplication.class)
@Import(TestContainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class AccountPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-11-05T09:30:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AccountJpaRepository jpaRepository;

    @Autowired
    AccountPersistenceAdapter adapter;

    private void insert(String currency, String status, Instant closedAt) {
        jdbc.update("""
                INSERT INTO accounts.accounts (id, owner_id, currency, status, created_at, closed_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), UUID.randomUUID(), currency, status,
                Timestamp.from(NOW), closedAt == null ? null : Timestamp.from(closedAt));
    }

    @Nested
    class RestricoesDaMigration {

        @Test
        void aceitaContaAtivaValida() {
            insert("BRL", "ACTIVE", null);

            assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts.accounts", Long.class)).isEqualTo(1);
        }

        @Test
        void aceitaContaEncerradaComClosedAt() {
            insert("BRL", "CLOSED", CLOSED_AT);
        }

        @Test
        void rejeitaMoedaNaoSuportada() {
            assertThatThrownBy(() -> insert("USD", "ACTIVE", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void rejeitaStatusDesconhecido() {
            assertThatThrownBy(() -> insert("BRL", "DELETED", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void rejeitaEncerradaSemClosedAt() {
            assertThatThrownBy(() -> insert("BRL", "CLOSED", null))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("closed_at_matches_status");
        }

        @Test
        void rejeitaClosedAtEmContaNaoEncerrada() {
            assertThatThrownBy(() -> insert("BRL", "ACTIVE", CLOSED_AT))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("closed_at_matches_status");
        }

        @Test
        void versionComecaEmZero() {
            insert("BRL", "ACTIVE", null);

            assertThat(jdbc.queryForObject("SELECT version FROM accounts.accounts", Long.class)).isZero();
        }
    }

    @Nested
    class EntidadeJpa {

        @Test
        void gravaELeTodosOsCampos() {
            UUID id = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            jpaRepository.saveAndFlush(new AccountJpaEntity(id, owner, "BRL", "CLOSED", NOW, CLOSED_AT));

            AccountJpaEntity loaded = jpaRepository.findById(id).orElseThrow();

            assertThat(loaded.getOwnerId()).isEqualTo(owner);
            assertThat(loaded.getCurrency()).isEqualTo("BRL");
            assertThat(loaded.getStatus()).isEqualTo("CLOSED");
            assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
            assertThat(loaded.getClosedAt()).isEqualTo(CLOSED_AT);
        }

        @Test
        void checkDoBancoTambemValeParaAEntidade() {
            assertThatThrownBy(() -> jpaRepository.saveAndFlush(
                    new AccountJpaEntity(UUID.randomUUID(), UUID.randomUUID(), "BRL", "CLOSED", NOW, null)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class Adapter {

        @Test
        void findByIdDeContaInexistenteRetornaVazio() {
            assertThat(adapter.findById(new AccountId(UUID.randomUUID()))).isEmpty();
        }

        @Test
        void saveEFindByIdFazemIdaEVoltaDoDominio() {
            AccountId id = new AccountId(UUID.randomUUID());
            PrincipalId owner = new PrincipalId(UUID.randomUUID());
            Account account = Account.open(id, owner, InstanceCurrency.BRL, NOW);

            adapter.save(account);
            Account loaded = adapter.findById(id).orElseThrow();

            assertThat(loaded.getId()).isEqualTo(id);
            assertThat(loaded.getOwnerId()).isEqualTo(owner);
            assertThat(loaded.getCurrency()).isEqualTo(InstanceCurrency.BRL);
            assertThat(loaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
            assertThat(loaded.getClosedAt()).isNull();
        }

        @Test
        void saveDeContaExistenteAtualizaOStatus() {
            AccountId id = new AccountId(UUID.randomUUID());
            adapter.save(Account.open(id, new PrincipalId(UUID.randomUUID()), InstanceCurrency.BRL, NOW));
            jpaRepository.flush();

            Account account = adapter.findById(id).orElseThrow();
            account.block();
            adapter.save(account);
            jpaRepository.flush();

            assertThat(jdbc.queryForObject("SELECT status FROM accounts.accounts WHERE id = ?", String.class, id.value()))
                    .isEqualTo("BLOCKED");
        }

        @Test
        void duasAtualizacoesSeguidasDaMesmaConta() {
            AccountId id = new AccountId(UUID.randomUUID());
            adapter.save(Account.open(id, new PrincipalId(UUID.randomUUID()), InstanceCurrency.BRL, NOW));
            jpaRepository.flush();

            Account first = adapter.findById(id).orElseThrow();
            first.block();
            adapter.save(first);
            jpaRepository.flush();

            Account second = adapter.findById(id).orElseThrow();
            second.unlock();
            adapter.save(second);
            jpaRepository.flush();

            assertThat(jdbc.queryForObject("SELECT status FROM accounts.accounts WHERE id = ?", String.class, id.value()))
                    .isEqualTo("ACTIVE");
        }
    }
}
