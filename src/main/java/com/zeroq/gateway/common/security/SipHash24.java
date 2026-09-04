package com.zeroq.gateway.common.security;

public final class SipHash24 {
    private SipHash24() {
    }

    public static long hash(byte[] key, byte[] message, int messageLength) {
        if (key.length != 16) {
            throw new IllegalArgumentException("SipHash key must be 16 bytes");
        }
        if (messageLength < 0 || messageLength > message.length) {
            throw new IllegalArgumentException("Invalid message length");
        }

        long k0 = littleEndianLong(key, 0);
        long k1 = littleEndianLong(key, 8);
        long[] state = {
                0x736f6d6570736575L ^ k0,
                0x646f72616e646f6dL ^ k1,
                0x6c7967656e657261L ^ k0,
                0x7465646279746573L ^ k1
        };

        int offset = 0;
        while (offset + 8 <= messageLength) {
            long block = littleEndianLong(message, offset);
            state[3] ^= block;
            sipRounds(state, 2);
            state[0] ^= block;
            offset += 8;
        }

        long tail = ((long) messageLength) << 56;
        for (int index = 0; offset + index < messageLength; index++) {
            tail |= ((long) message[offset + index] & 0xffL) << (index * 8);
        }
        state[3] ^= tail;
        sipRounds(state, 2);
        state[0] ^= tail;
        state[2] ^= 0xffL;
        sipRounds(state, 4);
        return state[0] ^ state[1] ^ state[2] ^ state[3];
    }

    private static void sipRounds(long[] state, int count) {
        for (int index = 0; index < count; index++) {
            state[0] += state[1];
            state[1] = Long.rotateLeft(state[1], 13);
            state[1] ^= state[0];
            state[0] = Long.rotateLeft(state[0], 32);
            state[2] += state[3];
            state[3] = Long.rotateLeft(state[3], 16);
            state[3] ^= state[2];
            state[0] += state[3];
            state[3] = Long.rotateLeft(state[3], 21);
            state[3] ^= state[0];
            state[2] += state[1];
            state[1] = Long.rotateLeft(state[1], 17);
            state[1] ^= state[2];
            state[2] = Long.rotateLeft(state[2], 32);
        }
    }

    private static long littleEndianLong(byte[] bytes, int offset) {
        long value = 0;
        for (int index = 0; index < 8; index++) {
            value |= ((long) bytes[offset + index] & 0xffL) << (index * 8);
        }
        return value;
    }
}
