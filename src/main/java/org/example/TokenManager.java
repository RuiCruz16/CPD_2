package org.example;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class TokenManager {
    private static final long TOKEN_VALIDITY_SECONDS = 1200;
    private final Map<String, TokenInfo> tokenMap = new HashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final ReentrantLock tokenMapLock = new ReentrantLock();

    public String generateToken(String username) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getEncoder().encodeToString(randomBytes);

        TokenInfo tokenInfo = new TokenInfo(username, Instant.now().plusSeconds(TOKEN_VALIDITY_SECONDS));
        tokenMapLock.lock();
        try {
            tokenMap.put(token, tokenInfo);
        } finally {
            tokenMapLock.unlock();
        }
        return token;
    }

    public String getUsernameFromToken(String token) {
        tokenMapLock.lock();
        try {
            TokenInfo tokenInfo = tokenMap.get(token);
            if (tokenInfo != null && tokenInfo.isValid()) {
                return tokenInfo.username;
            }
            return null;
        } finally {
            tokenMapLock.unlock();
        }
    }

    public void invalidateToken(String token) {
        tokenMapLock.lock();
        try {
            tokenMap.remove(token);
        } finally {
            tokenMapLock.unlock();
        }
    }

    public void cleanExpiredTokens() {
        Thread.startVirtualThread(() -> {
            tokenMapLock.lock();
            try {
                tokenMap.entrySet().removeIf(entry -> !entry.getValue().isValid());
            } finally {
                tokenMapLock.unlock();
            }
        });
    }

    private static class TokenInfo {
        private final String username;
        private final Instant expirationTime;

        public TokenInfo(String username, Instant expirationTime) {
            this.username = username;
            this.expirationTime = expirationTime;
        }

        public boolean isValid() {
            return Instant.now().isBefore(expirationTime);
        }
    }
}
