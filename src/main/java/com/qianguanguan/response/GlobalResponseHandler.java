package com.qianguanguan.response;


import com.qianguanguan.configuration.SecurityProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局响应处理器
 * <p>
 * 实现 ResponseBodyAdvice 统一包装响应体为 {@link Response} 格式，
 * 同时提供全局异常处理将业务异常转为标准 JSON 错误响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    private final SecurityProperties securityProperties;

    public GlobalResponseHandler(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * 全局异常处理
     * 将 AuthenticationException/AccessDeniedException 抛给 Security 框架处理，
     * 其余异常统一包装为 Response.error
     */
    @ExceptionHandler(value = Exception.class)
    @SuppressWarnings("unused") // 由 Spring MVC 反射调用（@ExceptionHandler），IDE 静态分析无法识别，非未使用
    public Response<Object> exceptionHandler(Exception e) throws Exception {
        log.error(e.getMessage(), e);

        if (e instanceof AuthenticationException || e instanceof AccessDeniedException) {
            throw e;
        }

        if (e instanceof NoResourceFoundException) {
            return Response.error(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
        }

        return Response.error(e.getMessage());
    }

    /**
     * 判断是否需要包装响应体
     * 已经是 Response 类型的返回值不再包装
     */
    @Override
    public boolean supports(MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return !returnType.getParameterType().equals(Response.class);
    }

    /**
     * 在响应体写入前统一包装
     * Swagger 路径和 String 类型特殊处理，其余用 Response.success 包装
     */
    @Override
    public Object beforeBodyWrite(Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {

        String path = request.getURI().getPath();
        for (String swaggerPath : securityProperties.getSwaggerPaths()) {
            if (path.startsWith(swaggerPath)) {
                return body;
            }
        }

        if (body instanceof String) {
            try {
                return new ObjectMapper().writeValueAsString(Response.success(body));
            } catch (JsonProcessingException e) {
                return Response.error(e.getMessage());
            }
        }
        return Response.success(body);
    }
}