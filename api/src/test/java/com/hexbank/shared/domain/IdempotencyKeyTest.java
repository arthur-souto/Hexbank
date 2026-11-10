package com.hexbank.shared.domain;

import com.hexbank.shared.domain.exceptions.InvalidIdempotencyKeyException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Especificação: docs/architecture/ARCHITECTURE.md, seção "IdempotencyKey" (BR-030, HTTP_API convenções, ADR-0004). */
class IdempotencyKeyTest {

    private static final String VALID = "Key-000000000001";

    /** SHR-IK-01 — Aceitar chaves válidas. */
    @Nested
    class ChavesValidas {

        @Test
        void aceitaOLimiteInferior() {
            String raw = "a".repeat(16);

            assertThat(IdempotencyKey.of(raw).value()).isEqualTo(raw);
        }

        @Test
        void aceitaOLimiteSuperior() {
            String raw = "a".repeat(128);

            assertThat(IdempotencyKey.of(raw).value()).isEqualTo(raw);
        }

        @Test
        void naoAparaEspacosNasPontas() {
            String raw = " abc-0000000-xyz ";

            assertThat(IdempotencyKey.of(raw).value()).isEqualTo(raw);
        }
    }

    /** SHR-IK-02 — Recusar chaves inválidas. */
    @Nested
    class ChavesInvalidas {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "                ", "\t\n                "})
        void recusaAusenteOuSoEspacos(String raw) {
            assertThatThrownBy(() -> IdempotencyKey.of(raw)).isInstanceOf(InvalidIdempotencyKeyException.class);
        }

        @Test
        void recusaAbaixoDoLimiteInferior() {
            assertThatThrownBy(() -> IdempotencyKey.of("a".repeat(15))).isInstanceOf(InvalidIdempotencyKeyException.class);
        }

        @Test
        void recusaAcimaDoLimiteSuperior() {
            assertThatThrownBy(() -> IdempotencyKey.of("a".repeat(129))).isInstanceOf(InvalidIdempotencyKeyException.class);
        }

        @Test
        void construtorDiretoTambemValida() {
            assertThatThrownBy(() -> new IdempotencyKey("curta")).isInstanceOf(InvalidIdempotencyKeyException.class);
        }

        @Test
        void mensagemDeErroNaoExpoeAChave() {
            String raw = "segredo-curto";

            assertThatThrownBy(() -> IdempotencyKey.of(raw))
                    .isInstanceOf(InvalidIdempotencyKeyException.class)
                    .message().doesNotContain(raw);
        }
    }

    /** SHR-IK-03 — Igualdade e sigilo. */
    @Nested
    class IgualdadeESigilo {

        @Test
        void chavesComOMesmoTextoSaoIguais() {
            IdempotencyKey a = IdempotencyKey.of(VALID);
            IdempotencyKey b = IdempotencyKey.of(VALID);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void diferenciaMaiusculasDeMinusculas() {
            assertThat(IdempotencyKey.of("Key-000000000001")).isNotEqualTo(IdempotencyKey.of("key-000000000001"));
        }

        @Test
        void toStringNaoExpoeOValor() {
            String text = IdempotencyKey.of(VALID).toString();

            assertThat(text).doesNotContain(VALID);
            assertThat(text).isEqualTo("IdempotencyKey[***]");
        }
    }
}
