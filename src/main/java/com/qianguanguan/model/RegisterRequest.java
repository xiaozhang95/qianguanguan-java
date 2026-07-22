package com.qianguanguan.model;

import lombok.Data;

@Data
public class RegisterRequest {

    private String username;

    private String password;

    private String captchaKey;

    private String captchaCode;
}
