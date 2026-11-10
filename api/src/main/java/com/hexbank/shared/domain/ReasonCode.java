package com.hexbank.shared.domain;

import java.util.regex.Pattern;

public final class ReasonCode {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*$");
    private static final int MAX_LENGTH = 64;

    private ReasonCode() {}

    public static void requireValid(String value) {
        if(value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Reason must be UPPER_SNAKE_CASE");
        }
    }
}
