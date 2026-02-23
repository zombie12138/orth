package com.abyss.orth.admin.web.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * In-memory login rate limiter to protect against brute force attacks.
 *
 * <p>Tracks failed login attempts per username. After {@link #MAX_ATTEMPTS} failures within {@link
 * #WINDOW_MS}, the account is locked for {@link #LOCKOUT_MS}.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final long WINDOW_MS = 60_000L;
    static final long LOCKOUT_MS = 5 * 60_000L;

    private final ConcurrentHashMap<String, LoginAttempt> attempts = new ConcurrentHashMap<>();

    /** Returns {@code true} if the username is currently locked out. */
    public boolean isLockedOut(String username) {
        LoginAttempt attempt = attempts.get(username);
        if (attempt == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        // Lockout expired — clear entry
        if (attempt.lockedUntil > 0 && now >= attempt.lockedUntil) {
            attempts.remove(username);
            return false;
        }

        return attempt.lockedUntil > 0;
    }

    /** Records a failed login attempt. May trigger lockout. */
    public void recordFailure(String username) {
        long now = System.currentTimeMillis();

        attempts.compute(
                username,
                (key, existing) -> {
                    if (existing == null || now - existing.windowStart > WINDOW_MS) {
                        // Start a new sliding window
                        LoginAttempt fresh = new LoginAttempt();
                        fresh.windowStart = now;
                        fresh.count.set(1);
                        return fresh;
                    }

                    int count = existing.count.incrementAndGet();
                    if (count >= MAX_ATTEMPTS) {
                        existing.lockedUntil = now + LOCKOUT_MS;
                    }
                    return existing;
                });
    }

    /** Clears failure history on successful login. */
    public void recordSuccess(String username) {
        attempts.remove(username);
    }

    private static class LoginAttempt {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);
        volatile long lockedUntil;
    }
}
