package com.qianguanguan.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 统一的 JSON 响应写入器
 * <p>
 * 供 Security 过滤器链（未认证/无权限/Token 过期等）与 MVC 层共用，
 * 保证所有错误响应都输出同一套 {@link Response} 格式。
 * 使用 Spring 容器中的 ObjectMapper，统一应用 Jackson 序列化配置。
 */
@Component
public class ResponseWriter {

    private final ObjectMapper objectMapper;

    public ResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 写入错误响应
     * <p>
     * HTTP 状态码保持 200，具体错误码（401/403/404/500 等）放在响应体的 code 字段，
     * 与项目现有前后端约定保持一致。
     */
    public void writeError(HttpServletResponse response, int code, String msg) throws IOException {
        writeJson(response, Response.error(msg, code));
    }

    /**
     * 写入任意 Response 对象为 JSON
     */
    public void writeJson(HttpServletResponse response, Response<?> responseBody) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(objectMapper.writeValueAsString(responseBody));
    }
}
