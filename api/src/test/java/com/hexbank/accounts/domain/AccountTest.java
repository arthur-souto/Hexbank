package com.hexbank.accounts.domain;

import com.hexbank.accounts.domain.exceptions.AccountNotActiveException;
import com.hexbank.accounts.domain.exceptions.InvalidStatusTransitionException;
import com.hexbank.identity.domain.PrincipalId;
import com.hexbank.shared.domain.InstanceCurrency;
import com.hexbank.shared.domain.exceptions.base.BusinessException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Especificação: docs/architecture/ARCHITECTURE.md, seção "Account — o que deve fazer e respeitar" (BR-002, BR-004, BR-005, ADR-0003). */
class AccountTest {

    private static final AccountId ID = new AccountId(UUID.fromString("a5420e4b-1092-4eac-bedd-21e774c4bc5e"));
    private static final PrincipalId OWNER = new PrincipalId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final PrincipalId OTHER = new PrincipalId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-11-05T09:30:00Z");

    private static Account active() {
        return Account.open(ID, OWNER, InstanceCurrency.BRL, NOW);
    }

    private static Account blocked() {
        return Account.restore(ID, OWNER, InstanceCurrency.BRL, AccountStatus.BLOCKED, NOW, null);
    }

    private static Account closed() {
        return Account.restore(ID, OWNER, InstanceCurrency.BRL, AccountStatus.CLOSED, NOW, CLOSED_AT);
    }

    @Nested
    class Open {

        @Test
        void criaContaAtivaComOsDadosInformados() {
            Account account = active();

            assertThat(account.getId()).isEqualTo(ID);
            assertThat(account.getOwnerId()).isEqualTo(OWNER);
            assertThat(account.getCurrency()).isEqualTo(InstanceCurrency.BRL);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.getCreatedAt()).isEqualTo(NOW);
        }

        @Test
        void nasceSemClosedAt() {
            assertThat(active().getClosedAt()).isNull();
        }

        @Test
        void cadaChamadaCriaUmaInstanciaNova() {
            assertThat(active()).isNotSameAs(active());
        }

        @Test
        void rejeitaCamposObrigatoriosNulos() {
            assertThatThrownBy(() -> Account.open(null, OWNER, InstanceCurrency.BRL, NOW)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Account.open(ID, null, InstanceCurrency.BRL, NOW)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Account.open(ID, OWNER, null, NOW)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Account.open(ID, OWNER, InstanceCurrency.BRL, null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Restore {

        @ParameterizedTest
        @EnumSource(AccountStatus.class)
        void reconstituiQualquerStatusSemAplicarTransicao(AccountStatus status) {
            Instant closedAt = status == AccountStatus.CLOSED ? CLOSED_AT : null;

            Account account = Account.restore(ID, OWNER, InstanceCurrency.BRL, status, NOW, closedAt);

            assertThat(account.getStatus()).isEqualTo(status);
            assertThat(account.getCreatedAt()).isEqualTo(NOW);
            assertThat(account.getClosedAt()).isEqualTo(closedAt);
        }

        @Test
        void rejeitaCamposObrigatoriosNulos() {
            assertThatThrownBy(() -> Account.restore(null, OWNER, InstanceCurrency.BRL, AccountStatus.ACTIVE, NOW, null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Account.restore(ID, null, InstanceCurrency.BRL, AccountStatus.ACTIVE, NOW, null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Account.restore(ID, OWNER, null, AccountStatus.ACTIVE, NOW, null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Account.restore(ID, OWNER, InstanceCurrency.BRL, null, NOW, null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Account.restore(ID, OWNER, InstanceCurrency.BRL, AccountStatus.ACTIVE, null, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejeitaContaEncerradaSemClosedAt() {
            assertThatThrownBy(() -> Account.restore(ID, OWNER, InstanceCurrency.BRL, AccountStatus.CLOSED, NOW, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("closedAt");
        }

        @Test
        void preservaOClosedAtGravado() {
            // Regressão: o closedAt vem do banco, não é recalculado a partir do createdAt.
            assertThat(closed().getClosedAt()).isEqualTo(CLOSED_AT).isNotEqualTo(NOW);
        }
    }

    @Nested
    class IsOwnedBy {

        @Test
        void verdadeiroParaODono() {
            assertThat(active().isOwnedBy(OWNER)).isTrue();
        }

        @Test
        void verdadeiroParaOutraInstanciaComMesmoUuid() {
            assertThat(active().isOwnedBy(new PrincipalId(OWNER.value()))).isTrue();
        }

        @Test
        void falsoParaOutroPrincipal() {
            // BR-004: base para o caso de uso responder 404 ACCOUNT_NOT_FOUND.
            assertThat(active().isOwnedBy(OTHER)).isFalse();
        }
    }

    @Nested
    class RequireActive {

        @Test
        void naoLancaQuandoAtiva() {
            assertThatCode(() -> active().requireActive()).doesNotThrowAnyException();
        }

        @Test
        void lancaQuandoBloqueada() {
            assertThatThrownBy(() -> blocked().requireActive())
                    .isInstanceOf(AccountNotActiveException.class)
                    .hasMessageContaining(ID.toString());
        }

        @Test
        void lancaQuandoEncerrada() {
            assertThatThrownBy(() -> closed().requireActive())
                    .isInstanceOf(AccountNotActiveException.class);
        }
    }

    @Nested
    class Block {

        @Test
        void ativaViraBloqueada() {
            Account account = active();

            account.block();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.BLOCKED);
        }

        @Test
        void bloquearContaJaBloqueadaNaoFazNada() {
            Account account = blocked();

            account.block();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.BLOCKED);
        }

        @Test
        void bloquearContaEncerradaLanca() {
            Account account = closed();

            assertThatThrownBy(account::block)
                    .isInstanceOf(InvalidStatusTransitionException.class);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        }
    }

    @Nested
    class Unlock {

        @Test
        void bloqueadaVoltaAAtiva() {
            Account account = blocked();

            account.unlock();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void desbloquearContaAtivaNaoFazNada() {
            Account account = active();

            account.unlock();

            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void desbloquearContaEncerradaLanca() {
            Account account = closed();

            assertThatThrownBy(account::unlock)
                    .isInstanceOf(InvalidStatusTransitionException.class);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        }
    }

    @Nested
    class Close {

        private static final Instant LATER = Instant.parse("2026-12-01T00:00:00Z");

        @Test
        void ativaViraEncerradaComOInstanteInformado() {
            Account account = active();

            account.close(CLOSED_AT);

            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
            assertThat(account.getClosedAt()).isEqualTo(CLOSED_AT);
        }

        @Test
        void bloqueadaTambemPodeSerEncerrada() {
            Account account = blocked();

            account.close(CLOSED_AT);

            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
            assertThat(account.getClosedAt()).isEqualTo(CLOSED_AT);
        }

        @Test
        void encerrarContaJaEncerradaNaoFazNadaEMantemClosedAtOriginal() {
            Account account = closed();

            account.close(LATER);

            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
            assertThat(account.getClosedAt()).isEqualTo(CLOSED_AT);
        }

        @Test
        void contaEncerradaNaoPodeMaisOperar() {
            Account account = active();

            account.close(CLOSED_AT);

            assertThatThrownBy(account::requireActive).isInstanceOf(AccountNotActiveException.class);
        }
    }

    /** Invariante: closedAt preenchido se e somente se status = CLOSED (ADR-0003, V2 closed_at_matches_status). */
    @Nested
    class InvarianteDoClosedAt {

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "BLOCKED"})
        void restoreRejeitaClosedAtEmContaNaoEncerrada(AccountStatus status) {
            assertThatThrownBy(() -> Account.restore(ID, OWNER, InstanceCurrency.BRL, status, NOW, CLOSED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void closeRejeitaInstanteNuloSemMudarOStatus() {
            Account account = active();

            assertThatThrownBy(() -> account.close(null)).isInstanceOf(NullPointerException.class);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }
    }

    @Test
    void excecoesDeContaSaoBusinessException() {
        assertThatThrownBy(() -> closed().requireActive()).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> closed().block()).isInstanceOf(BusinessException.class);
    }
}
