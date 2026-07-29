package com.qianguanguan.util;

import com.qianguanguan.constant.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;

import java.security.Key;
import java.util.Date;

@Slf4j
public class Jwt {

    public static String generateToken(long id, String role, Key key, Date date, Date expirationDate) {
        return "Bearer " + Jwts.builder()
                .setSubject(null)
                .claim(JwtClaims.DATE, date)
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.ID, id)
                .setIssuedAt(date)
                .setExpiration(expirationDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public static Claims parserToken(String token, Key key) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static Claims parserTokenLenient(String token, Key key) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}
