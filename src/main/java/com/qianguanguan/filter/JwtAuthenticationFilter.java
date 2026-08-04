package com.qianguanguan.filter;

import com.qianguanguan.configuration.SecurityProperties;
import com.qianguanguan.constant.JwtClaims;
import com.qianguanguan.response.ResponseWriter;
import com.qianguanguan.util.Jwt;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Key;
import java.util.Date;
import java.util.List;

/**
 * JWT 认证过滤器
 * <p>
 * 每次请求只执行一次，负责：
 * <ol>
 *   <li>白名单路径直接放行</li>
 *   <li>解析 Authorization 头中的 Bearer Token</li>
 *   <li>校验 Token 有效性（签名 + Redis 缓存时间戳）</li>
 *   <li>通过后将用户信息写入 SecurityContext 和 Request 属性</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final StringRedisTemplate stringRedisTemplate;
    private final SecurityProperties securityProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ResponseWriter responseWriter;

    public JwtAuthenticationFilter(StringRedisTemplate stringRedisTemplate,
                                   SecurityProperties securityProperties,
                                   ResponseWriter responseWriter) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.securityProperties = securityProperties;
        this.responseWriter = responseWriter;
    }

    /**
     * 核心过滤逻辑
     * 白名单放行 → 解析 Token → Redis 校验 → 设置 SecurityContext
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        // 白名单路径直接放行
        String requestPath = request.getRequestURI();
        for (String whitelistPath : securityProperties.getAllWhitelist()) {
            if (pathMatcher.match(whitelistPath, requestPath)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 解析 Bearer Token
        String Authorization = request.getHeader("Authorization");
        if (Authorization != null && Authorization.startsWith("Bearer ")) {
            try {
                var jwtConfig = securityProperties.getJwt();
                Key key = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes());
                String token = Authorization.substring(7);
                Claims claims = Jwt.parserToken(token, key);

                String role = claims.get(JwtClaims.ROLE, String.class);
                Long id = claims.get(JwtClaims.ID, Long.class);

                // 与 Redis 中存储的签发时间戳对比，防止伪造或过期 Token
                Date date = claims.get(JwtClaims.DATE, Date.class);
                String cacheDate = stringRedisTemplate.opsForValue().get("user_" + id);

                if (cacheDate != null && Long.parseLong(cacheDate) == date.getTime()) {
                    // 设置 Spring Security 认证信息
                    var authentication = new UsernamePasswordAuthenticationToken(
                            null, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // 将用户信息写入 Request 属性，方便 Controller 层直接获取
                    request.setAttribute(JwtClaims.ROLE, role);
                    request.setAttribute(JwtClaims.ID, id);
                } else {
                    responseWriter.writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "token过期");
                    return;
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
