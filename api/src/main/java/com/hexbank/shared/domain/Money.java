package com.hexbank.shared.domain;

import com.hexbank.shared.domain.exceptions.DifferentCurrencyException;

import java.util.Objects;

public record Money(InstanceCurrency currency, long minorUnits) {

    public Money {
        Objects.requireNonNull(currency, "Currency");
    }

    public boolean isPositive() {
        return minorUnits > 0;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(currency, Math.addExact(minorUnits, other.minorUnits));
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(currency, Math.subtractExact(minorUnits, other.minorUnits));
    }

    private void requireSameCurrency(Money other) {
        if(!currency.equals(other.currency)) {
            throw new DifferentCurrencyException("Different currencies: " + currency + " and " + other.currency);
        }
    }
}
