package com.hexbank.accounts.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountIdTest {

    private static final String RAW = "a5420e4b-1092-4eac-bedd-21e774c4bc5e";

    @Test
    void ofConverteTextoEmUuid() {
        assertThat(AccountId.of(RAW).value()).isEqualTo(UUID.fromString(RAW));
    }

    @Test
    void igualdadePorValor() {
        assertThat(AccountId.of(RAW)).isEqualTo(new AccountId(UUID.fromString(RAW)));
        assertThat(AccountId.of(RAW)).hasSameHashCodeAs(new AccountId(UUID.fromString(RAW)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "nao-e-uuid", "a5420e4b-1092-4eac-bedd"})
    void ofRejeitaTextoInvalido(String raw) {
        assertThatThrownBy(() -> AccountId.of(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofRejeitaNulo() {
        assertThatThrownBy(() -> AccountId.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void construtorRejeitaNulo() {
        assertThatThrownBy(() -> new AccountId(null)).isInstanceOf(NullPointerException.class);
    }
}
