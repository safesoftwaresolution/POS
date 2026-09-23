package com.sciencebot.pos.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${security.jwt.secret}")
    private String secretKey;

    @Value("${security.jwt.expiration-ms}")
    private long jwtExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, claims -> claims.getSubject());
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails, String role, Long storeId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", role);
        if (storeId != null) {
            extraClaims.put("storeId", storeId);
        }
        return generateToken(extraClaims, userDetails);
    }

    public String generateToken(UserDetails userDetails, String role) {
        return generateToken(userDetails, role, null);
    }

    public String generateToken(String username, String role, Long storeId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", role);
        if (storeId != null) {
            extraClaims.put("storeId", storeId);
        }
        return buildToken(extraClaims, username);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails.getUsername());
    }

    private String buildToken(Map<String, Object> extraClaims, String subject) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (userDetails.getUsername().equals(username)) && !isTokenExpired(token);
    }

    public String extractRole(String token) {
        try {
            Object roleClaim = extractAllClaims(token).get("role");
            return roleClaim == null ? null : roleClaim.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public Long extractStoreId(String token) {
        try {
            Object storeIdClaim = extractAllClaims(token).get("storeId");
            if (storeIdClaim == null) return null;
            if (storeIdClaim instanceof Long l) return l;
            if (storeIdClaim instanceof Integer i) return i.longValue();
            if (storeIdClaim instanceof Number n) return n.longValue();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, claims -> claims.getExpiration());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = this.secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public long getExpirationTime() {
        return jwtExpiration / 1000;
    }
}