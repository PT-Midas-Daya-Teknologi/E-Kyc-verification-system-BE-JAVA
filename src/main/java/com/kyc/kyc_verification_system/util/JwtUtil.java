package com.kyc.kyc_verification_system.util;

import java.security.Key;
import java.util.Date;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class JwtUtil {

    private static final String SECRET_KEY =
            "mysecretkeymysecretkeymysecretkey12";

    private static final Key KEY =
            Keys.hmacShaKeyFor(SECRET_KEY.getBytes());

    private static final long EXPIRATION_TIME =
            10 * 60 * 1000;

    public static String generateToken(String sessionId) {

        return Jwts.builder()

                .setSubject(sessionId)

                .setIssuedAt(new Date())

                .setExpiration(
                        new Date(System.currentTimeMillis() + EXPIRATION_TIME)
                )

                .signWith(KEY, SignatureAlgorithm.HS256)

                .compact();
    }

    public static String extractSessionId(String token) {

        Claims claims = extractAllClaims(token);

        return claims.getSubject();
    }

    public static boolean validateToken(String token) {

        try {

            extractAllClaims(token);

            return true;

        } catch (Exception e) {

            return false;
        }
    }

    public static Date extractExpiration(String token) {

        Claims claims = extractAllClaims(token);

        return claims.getExpiration();
    }

    public static boolean isTokenExpired(String token) {

        return extractExpiration(token).before(new Date());
    }

    private static Claims extractAllClaims(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}