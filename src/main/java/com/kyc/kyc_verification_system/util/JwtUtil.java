package com.kyc.kyc_verification_system.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String SECRET_KEY =
            "mysecretkeymysecretkeymysecretkey12";

    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(
                    SECRET_KEY.getBytes()
            );

    private static final long EXPIRATION_TIME =
            10 * 60 * 1000;

    public String generateToken(
            String sessionId) {

        return Jwts.builder()
                .subject(sessionId)
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + EXPIRATION_TIME
                        )
                )
                .signWith(
                        KEY
                )
                .compact();
    }

    public static  String extractSessionId(
            String token) {

        Claims claims =
                extractAllClaims(token);

        return claims.getSubject();
    }

    public boolean validateToken(
            String token) {

        try {

            extractAllClaims(token);

            return !isTokenExpired(token);

        } catch (Exception e) {

            return false;
        }
    }

    public Date extractExpiration(
            String token) {

        Claims claims =
                extractAllClaims(token);

        return claims.getExpiration();
    }

    public boolean isTokenExpired(
            String token) {

        return extractExpiration(token)
                .before(new Date());
    }

    private static Claims extractAllClaims(
            String token) {

        return Jwts
                .parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}