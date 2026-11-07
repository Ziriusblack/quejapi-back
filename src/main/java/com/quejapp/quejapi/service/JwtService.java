package com.quejapp.quejapi.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.impl.lang.Function;
import io.jsonwebtoken.lang.Objects;
import io.jsonwebtoken.security.Keys;
import io.micrometer.common.util.StringUtils;

@Service
public class JwtService {

    // 1. Inyectamos la clave secreta desde el application.properties
    @Value("${jwt.secret}")
    private String secretKey;

    private final UserDetailsService userDetailsService;

    public JwtService(UserDetailsService userDetailsService){
        this.userDetailsService = userDetailsService;
        // Eliminamos la generación de clave aleatoria que estaba aquí
    }

    public String extractUserName(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(
            Map<String, Objects> extraClaims,
            UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", userDetails.getAuthorities())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // 1 day
                .signWith(getSignInKey())
                .compact();
    }

    public Date getExpirationDateFromToken(String token) {
        return extractExpiration(token);
    }

    public Boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUserName(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(getSignInKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateRefresh(Map<String, Objects> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 604800000))
                .signWith(getSignInKey())
                .compact();
    }

    public String getEmailFromToken(String token) {
        return extractUserName(token);
    }

    public Boolean validateToken(String token) {
        String userEmail = extractUserName(token);
        if (StringUtils.isNotEmpty(userEmail) && !isTokenExpired(token)) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
            return isTokenValid(token, userDetails);
        }
        return false;
    }
}