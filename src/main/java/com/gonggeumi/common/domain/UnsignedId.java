package com.gonggeumi.common.domain;

import java.math.BigInteger;

/** MySQL BIGINT UNSIGNED 식별자. JSON 경계에서는 decimal string으로 전달한다. */
public record UnsignedId(BigInteger value) {
    public static final BigInteger MAX = new BigInteger("18446744073709551615");

    public UnsignedId {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("ID is outside the unsigned BIGINT range");
        }
    }

    public static UnsignedId parse(String text) {
        if (text == null || !text.matches("[1-9][0-9]{0,19}")) {
            throw new IllegalArgumentException("ID must be a positive decimal string");
        }
        return new UnsignedId(new BigInteger(text));
    }

    @Override public String toString() { return value.toString(); }
}
