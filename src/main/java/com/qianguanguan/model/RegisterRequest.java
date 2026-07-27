package com.qianguanguan.model;

import lombok.Data;

@Data
public class RegisterRequest {

    private String password;

    private String email;

    private String emailCode;

    private String captchaKey;

    private String captchaCode;
}
