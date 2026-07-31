package com.qianguanguan.model;

import lombok.Data;

/**
 * 分类新增/更新请求参数
 */
@Data
public class CategorySaveRequest {

    /** 分类名称 */
    private String name;

    /** 图标（emoji 或图片路径） */
    private String icon;

    /** 分类类型：INCOME-收入 / EXPENSE-支出 */
    private String type;

    /** 排序序号 */
    private Integer sort;
}
