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


@Slf4j
@RestControllerAdvice
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    private final SecurityProperties securityProperties;

    public GlobalResponseHandler(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @ExceptionHandler(value = Exception.class)
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

    @Override
    public boolean supports(MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return !returnType.getParameterType().equals(Response.class);
    }

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