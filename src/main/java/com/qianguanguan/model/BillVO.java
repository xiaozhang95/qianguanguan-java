package com.qianguanguan.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 账单视图 VO
 * 包含分类名称和图标等前端展示所需字段
 */
@Data
@Builder
public class BillVO {

    /** 账单ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 分类ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 分类图标 */
    private String categoryIcon;

    /** 账单类型 */
    private String type;

    /** 金额 */
    private BigDecimal amount;

    /** 账单日期 */
    private LocalDate billDate;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createtime;
}
