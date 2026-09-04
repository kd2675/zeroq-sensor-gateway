package com.zeroq.gateway.common.security;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class SipHash24Tests {
    @Test
    void hash_referenceVectorForFifteenByteMessage_matchesSpecification() {
        byte[] key = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f");
        byte[] message = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e");

        long hash = SipHash24.hash(key, message, message.length);

        assertThat(Long.toUnsignedString(hash, 16)).isEqualTo("a129ca6149be45e5");
    }
}
