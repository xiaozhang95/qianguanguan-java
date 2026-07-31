package com.qianguanguan.util;

import com.qianguanguan.constant.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;

import java.security.Key;
import java.util.Date;

/**
 * JWT 工具类
 * <p>
 * 提供 Token 生成、解析、宽松解析（允许过期）等静态方法
 */
@Slf4j
public class Jwt {

    /**
     * 生成 JWT Token（带 Bearer 前缀）
     *
     * @param id             用户 ID
     * @param role           用户角色
     * @param key            签名密钥
     * @param date           签发时间
     * @param expirationDate 过期时间
     * @return Bearer xxx 格式的 Token
     */
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

    /**
     * 严格解析 Token（过期会抛异常）
     */
    public static Claims parserToken(String token, Key key) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 宽松解析 Token（允许过期，用于 Token 刷新）
     */
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
