package com.qianguanguan.model;

import lombok.Data;

@Data
public class CategorySaveRequest {

    private String name;

    private String icon;

    private String type;

    private Integer sort;
}
