package com.lhs.lawmind.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * JWT 工具类
 * 支持双 Token 机制：
 * - accessToken：短期有效（默认2小时），用于接口鉴权
 * - refreshToken：长期有效（默认7天），用于刷新 accessToken
 */
@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    /** Token 类型标识 claim key */
    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    /** accessToken 有效期（秒），默认 7200 = 2小时 */
    @Value("${jwt.expire:7200}")
    private long expire;

    /** refreshToken 有效期（秒），默认 604800 = 7天 */
    @Value("${jwt.refresh-expire:604800}")
    private long refreshExpire;

    /**
     * 生成 accessToken（短期令牌）
     *
     * @param userId 用户ID
     * @return accessToken 字符串
     */
    public String generateToken(String userId, String role) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expire * 1000);
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_ROLE, role != null ? role : "user")
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    /** @deprecated use {@link #generateToken(String, String)} instead */
    @Deprecated
    public String generateToken(String userId) {
        return generateToken(userId, "user");
    }

    /**
     * 生成 refreshToken（长期令牌）
     *
     * @param userId 用户ID
     * @return refreshToken 字符串
     */
    public String generateRefreshToken(String userId, String role) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + refreshExpire * 1000);
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .claim(CLAIM_ROLE, role != null ? role : "user")
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    /** @deprecated use {@link #generateRefreshToken(String, String)} instead */
    @Deprecated
    public String generateRefreshToken(String userId) {
        return generateRefreshToken(userId, "user");
    }

    /**
     * 解析 Token 中的 Claims
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 验证 Token 是否有效（未过期且签名正确）
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Token 中获取用户ID
     */
    public String getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    /**
     * 从 Token 中获取用户角色
     */
    public String getRoleFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            String role = claims.get(CLAIM_ROLE, String.class);
            return role != null ? role : "user";
        } catch (Exception e) {
            return "user";
        }
    }

    /**
     * 判断 Token 是否为 refreshToken
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            String type = claims.get(CLAIM_TOKEN_TYPE, String.class);
            return TOKEN_TYPE_REFRESH.equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 accessToken 有效期（秒）
     */
    public long getExpire() {
        return expire;
    }
}
