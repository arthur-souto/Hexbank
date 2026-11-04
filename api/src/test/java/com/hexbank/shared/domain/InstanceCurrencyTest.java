package com.hexbank.shared.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class InstanceCurrencyTest {

    @Test
    void mvpSuportaApenasBrl() {
        assertThat(InstanceCurrency.values()).containsExactly(InstanceCurrency.BRL);
    }

    @Test
    void brlTemCodigoESimbolo() {
        assertThat(InstanceCurrency.BRL.getCurrencyCode()).isEqualTo("BRL");
        assertThat(InstanceCurrency.BRL.getCurrencySymbol()).isEqualTo("R$");
    }

    @ParameterizedTest
    @EnumSource(InstanceCurrency.class)
    void codigoEUmIso4217Valido(InstanceCurrency currency) {
        assertThatCode(() -> Currency.getInstance(currency.getCurrencyCode()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(InstanceCurrency.class)
    void codigoCoincideComONomeDaConstante(InstanceCurrency currency) {
        assertThat(currency.getCurrencyCode()).isEqualTo(currency.name());
    }

    @ParameterizedTest
    @EnumSource(InstanceCurrency.class)
    void moedaTemDuasCasasDecimais(InstanceCurrency currency) {
        // minorUnits assume centavos; uma moeda com outra escala exigiria revisão (ADR-0002, multimoeda).
        assertThat(Currency.getInstance(currency.getCurrencyCode()).getDefaultFractionDigits()).isEqualTo(2);
    }
}
