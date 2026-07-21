package com.qianguanguan.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "JWT token", example = "Bearer eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "用户信息")
    private UserInfo userInfo;

    @Data
    @Builder
    @Schema(description = "用户信息")
    public static class UserInfo {
        @Schema(description = "用户ID")
        private Long id;

        @Schema(description = "用户名")
        private String username;

        @Schema(description = "姓名")
        private String name;

        @Schema(description = "角色")
        private String role;

        @Schema(description = "手机号")
        private String phone;

        @Schema(description = "邮箱")
        private String email;

        @Schema(description = "头像")
        private String avatar;

        @Schema(description = "创建时间")
        private Date createtime;
    }
}
