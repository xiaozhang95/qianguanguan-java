package com.qianguanguan.constant;

import lombok.Getter;

@Getter
public enum JwtClaimKey {
    DATE("Date"),
    ROLE("role"),
    ID("id");

    private final String key;

    JwtClaimKey(String key) {
        this.key = key;
    }
}
