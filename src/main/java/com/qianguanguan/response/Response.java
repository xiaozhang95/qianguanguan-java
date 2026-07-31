package com.qianguanguan.response;

import jakarta.servlet.http.HttpServletResponse;
import lombok.Builder;
import lombok.Data;

/**
 * 统一 API 响应封装
 * <p>
 * 所有接口返回值均通过此类包装为 {code, msg, data} 格式
 * @param <T> 响应数据类型
 */
@Data
@Builder
public class Response<T> {
    /** 状态码，默认 200 */
    @Builder.Default
    private int code = HttpServletResponse.SC_OK;

    /** 提示信息，默认"操作成功" */
    @Builder.Default
    private String msg = "操作成功";

    /** 响应数据 */
    private T data;

    /**
     * 构建成功响应
     */
    public static <T> Response<T> success(T data) {
        return Response.<T>builder().data(data).build();
    }

    /**
     * 构建失败响应（默认 code=500）
     */
    public static <T> Response<T> error(String msg) {
        return Response.<T>builder().code(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).msg(msg).build();
    }

    /**
     * 构建失败响应（指定状态码）
     */
    public static <T> Response<T> error(String msg, int code) {
        return Response.<T>builder().code(code).msg(msg).build();
    }
}