package com.qianguanguan.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录请求参数")
public class LoginRequest {

    @Schema(description = "邮箱", example = "admin@qq.com")
    private String email;

    @Schema(description = "密码", example = "123456")
    private String password;

    @Schema(description = "验证码key", example = "uuid-string")
    private String captchaKey;

    @Schema(description = "验证码", example = "A3fG")
    private String captchaCode;
}
