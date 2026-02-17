package com.xxl.job.admin.web.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.Resource;

/**
 * JWT token provider for generating and validating access/refresh tokens.
 *
 * <p>Uses HS256 signing algorithm with configurable secret and expiration times.
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_PERMISSION = "permission";
    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    @Resource private JwtProperties jwtProperties;

    public String generateAccessToken(JwtUserInfo userInfo) {
        return generateToken(userInfo, TOKEN_TYPE_ACCESS, jwtProperties.getAccessTokenExpiration());
    }

    public String generateRefreshToken(JwtUserInfo userInfo) {
        return generateToken(
                userInfo, TOKEN_TYPE_REFRESH, jwtProperties.getRefreshTokenExpiration());
    }

    public JwtUserInfo validateAccessToken(String token) {
        return validateToken(token, TOKEN_TYPE_ACCESS);
    }

    public JwtUserInfo validateRefreshToken(String token) {
        return validateToken(token, TOKEN_TYPE_REFRESH);
    }

    private String generateToken(JwtUserInfo userInfo, String tokenType, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claim(CLAIM_USER_ID, userInfo.getUserId())
                .claim(CLAIM_USERNAME, userInfo.getUsername())
                .claim(CLAIM_ROLE, userInfo.getRole())
                .claim(CLAIM_PERMISSION, userInfo.getPermission())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    private JwtUserInfo validateToken(String token, String expectedType) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(getSigningKey())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (!expectedType.equals(tokenType)) {
                logger.debug(
                        "Token type mismatch: expected={}, actual={}", expectedType, tokenType);
                return null;
            }

            JwtUserInfo userInfo = new JwtUserInfo();
            userInfo.setUserId(((Number) claims.get(CLAIM_USER_ID)).intValue());
            userInfo.setUsername(claims.get(CLAIM_USERNAME, String.class));
            userInfo.setRole(((Number) claims.get(CLAIM_ROLE)).intValue());
            userInfo.setPermission(claims.get(CLAIM_PERMISSION, String.class));
            return userInfo;
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
