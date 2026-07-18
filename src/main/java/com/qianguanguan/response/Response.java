package com.qianguanguan.response;

import jakarta.servlet.http.HttpServletResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Response<T> {
    @Builder.Default
    private int code = HttpServletResponse.SC_OK;

    @Builder.Default
    private String msg = "操作成功";

    private T data;

    public static <T> Response<T> success(T data) {
        return Response.<T>builder().data(data).build();
    }

    public static <T> Response<T> error(String msg) {
        return Response.<T>builder().code(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).msg(msg).build();
    }

    public static <T> Response<T> error(String msg, int code) {
        return Response.<T>builder().code(code).msg(msg).build();
    }
}