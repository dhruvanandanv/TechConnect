package com.techconnect.service;

import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.List;

public interface JwtService {

    String generateToken(UserDetails userDetails);

    String generateToken(String email, List<String> roles);

    String extractEmail(String token);

    List<String> extractRoles(String token);

    boolean isTokenValid(String token, UserDetails userDetails);

    boolean isTokenValid(String token);

    boolean isTokenExpired(String token);

    Date extractExpiration(String token);

    long getExpirationMs();
}
