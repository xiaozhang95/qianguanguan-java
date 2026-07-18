package com.qianguanguan.filter;

import com.qianguanguan.configuration.SecurityProperties;
import com.qianguanguan.constant.JwtClaimKey;
import com.qianguanguan.response.Response;
import com.qianguanguan.util.Jwt;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final StringRedisTemplate stringRedisTemplate;
    private final SecurityProperties securityProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(StringRedisTemplate stringRedisTemplate, SecurityProperties securityProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        for (String whitelistPath : securityProperties.getAllWhitelist()) {
            if (pathMatcher.match(whitelistPath, requestPath)) {
                chain.doFilter(request, response);
                return;
            }
        }

        String Authorization = request.getHeader("Authorization");
        if (Authorization != null && Authorization.startsWith("Bearer ")) {
            try {
                var jwtConfig = securityProperties.getJwt();
                Key key = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes());
                String token = Authorization.substring(7);
                Claims claims = Jwt.parserToken(token, key);

                String role = claims.get(JwtClaimKey.ROLE.getKey(), String.class);
                Long id = claims.get(JwtClaimKey.ID.getKey(), Long.class);

                Date date = claims.get(JwtClaimKey.DATE.getKey(), Date.class);
                String cacheDate = stringRedisTemplate.opsForValue().get("user_" + id);

                if (cacheDate != null && Long.parseLong(cacheDate) == date.getTime()) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            null, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    request.setAttribute(JwtClaimKey.ROLE.getKey(), role);
                    request.setAttribute(JwtClaimKey.ID.getKey(), id);
                } else {
                    writeJsonResponse(response);
                    return;
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    private void writeJsonResponse(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        Response<Object> rsp = Response.error("token过期", HttpServletResponse.SC_UNAUTHORIZED);
        ObjectMapper objectMapper = new ObjectMapper();
        response.getWriter().write(objectMapper.writeValueAsString(rsp));
    }
}