package com.qianguanguan.model;

import lombok.Data;

/**
 * 分类列表查询请求参数
 */
@Data
public class CategoryListRequest {

    /** 分类类型：INCOME-收入 / EXPENSE-支出 */
    private String type;
}
