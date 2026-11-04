package com.hexbank.shared.domain;

public enum InstanceCurrency {
    BRL("BRL", "R$");

    private final String currencyCode;
    private final String currencySymbol;

    InstanceCurrency(String currencyCode, String currencySymbol) {
        this.currencyCode = currencyCode;
        this.currencySymbol = currencySymbol;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }
}
