package com.techconnect.service.impl;

import com.techconnect.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class JwtServiceImpl implements JwtService {

    private final String jwtSecret;
    private final long jwtExpirationMs;
    private SecretKey signingKey;

    public JwtServiceImpl(
            @Value("${security.jwt.secret:}") String jwtSecret,
            @Value("${security.jwt.expiration-ms:3600000}") long jwtExpirationMs) {
        this.jwtSecret = jwtSecret;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    @PostConstruct
    public void init() {
        validateSecret();
        this.signingKey = createSigningKey(this.jwtSecret);
    }

    public void validateSecret() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT Secret is not configured or is empty. Please configure the TECHCONNECT_JWT_SECRET environment variable."
            );
        }
        byte[] keyBytes = resolveKeyBytes(jwtSecret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT Secret must provide at least 256 bits (32 bytes) of entropy for HMAC-SHA256. Current key length: "
                            + (keyBytes.length * 8) + " bits."
            );
        }
    }

    private SecretKey createSigningKey(String secret) {
        byte[] keyBytes = resolveKeyBytes(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] resolveKeyBytes(String secret) {
        if (secret == null) {
            return new byte[0];
        }
        String trimmed = secret.trim();
        try {
            byte[] decoded = Decoders.BASE64.decode(trimmed);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (Exception ignored) {
            // Secret is not Base64 encoded; fall through to UTF-8 bytes
        }
        return trimmed.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String generateToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return generateToken(userDetails.getUsername(), roles);
    }

    @Override
    public String generateToken(String email, List<String> roles) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expiryDate = new Date(nowMillis + jwtExpirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(this.signingKey != null ? this.signingKey : createSigningKey(this.jwtSecret), Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String email = extractEmail(token);
            return (email != null 
                    && email.equalsIgnoreCase(userDetails.getUsername()) 
                    && !isTokenExpired(token)
                    && userDetails.isEnabled());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getSubject() != null && !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    @Override
    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    @Override
    public long getExpirationMs() {
        return this.jwtExpirationMs;
    }

    private Claims extractAllClaims(String token) {
        SecretKey key = this.signingKey != null ? this.signingKey : createSigningKey(this.jwtSecret);
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
