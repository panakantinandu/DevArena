package com.devarena.common;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public final class Ids {
    private static final SecureRandom RANDOM = new SecureRandom();
    // No 0/O/1/I/L so codes are easy to read aloud.
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private Ids() {}

    public static UUID newId() { return UUID.randomUUID(); }

    public static String roomCode(int length) {
        char[] out = new char[length];
        for (int i = 0; i < length; i++) out[i] = CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)];
        return new String(out);
    }

    public static String token(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
