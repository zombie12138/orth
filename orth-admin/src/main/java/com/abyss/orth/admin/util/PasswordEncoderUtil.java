package com.abyss.orth.admin.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Password hashing utility using BCrypt.
 *
 * <p>Supports transparent migration from legacy SHA256 hashes: BCrypt hashes start with {@code
 * $2a$}, while SHA256 hashes are 64-character hex strings.
 */
public class PasswordEncoderUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String BCRYPT_PREFIX = "$2";
    private static final int SHA256_HEX_LENGTH = 64;

    private PasswordEncoderUtil() {}

    /** Hashes a raw password using BCrypt. */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /** Checks a raw password against a BCrypt hash. */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }

    /** Returns {@code true} if the stored hash is a legacy SHA256 (64-char hex) format. */
    public static boolean isLegacySha256(String storedHash) {
        return storedHash != null
                && storedHash.length() == SHA256_HEX_LENGTH
                && !storedHash.startsWith(BCRYPT_PREFIX);
    }
}
