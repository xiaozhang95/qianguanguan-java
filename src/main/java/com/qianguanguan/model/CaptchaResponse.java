package com.qianguanguan.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "验证码响应")
public class CaptchaResponse {

    @Schema(description = "验证码key，登录时需回传", example = "uuid-string")
    private String captchaKey;

    @Schema(description = "验证码图片(base64)", example = "data:image/png;base64,...")
    private String captchaImage;
}
