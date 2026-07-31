package com.qianguanguan.configuration;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianguanguan.filter.JwtAuthenticationFilter;
import com.qianguanguan.response.Response;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Spring Security 安全配置
 * <p>
 * 基于 JWT 的无状态认证方案：关闭 CSRF/表单登录/HTTP Basic/Session，
 * 白名单路径放行，其余请求由 JwtAuthenticationFilter 拦截校验
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(SecurityProperties securityProperties, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.securityProperties = securityProperties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 配置安全过滤链
     * <ul>
     *   <li>关闭 CSRF、表单登录、HTTP Basic、默认登出</li>
     *   <li>无状态 Session 管理</li>
     *   <li>白名单路径直接放行</li>
     *   <li>未认证返回 401 JSON，无权限返回 403 JSON</li>
     *   <li>在 UsernamePasswordAuthenticationFilter 之前插入 JWT 过滤器</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // 关闭 CSRF
                .formLogin(AbstractHttpConfigurer::disable) // 禁用默认登录页
                .httpBasic(AbstractHttpConfigurer::disable) // 禁用HTTP Basic认证
                .logout(AbstractHttpConfigurer::disable) // 禁用默认登出
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 无状态
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(securityProperties.getAllWhitelist()).permitAll()
                        .anyRequest().authenticated() // 其他都要认证
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            // 处理未认证的请求（401）
                            writeJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage());
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            // 处理无权限的请求（403）
                            writeJsonResponse(response, HttpServletResponse.SC_FORBIDDEN, accessDeniedException.getMessage());
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 内存用户详情服务（占位）
     * 实际用户认证由 JWT 过滤器处理，此处仅满足 Security 框架要求
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    /**
     * BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 向客户端写入 JSON 格式的错误响应
     */
    private void writeJsonResponse(HttpServletResponse response, int code, String msg) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        Response<Object> rsp = Response.error(msg, code);
        ObjectMapper objectMapper = new ObjectMapper();
        response.getWriter().write(objectMapper.writeValueAsString(rsp));
    }
}