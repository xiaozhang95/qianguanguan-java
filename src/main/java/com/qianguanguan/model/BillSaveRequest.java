package com.qianguanguan.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 账单新增/更新请求参数
 */
@Data
public class BillSaveRequest {

    /** 分类ID */
    private Long categoryId;

    /** 账单类型：INCOME-收入 / EXPENSE-支出 */
    private String type;

    /** 金额 */
    private BigDecimal amount;

    /** 账单日期 */
    private LocalDate billDate;

    /** 备注 */
    private String remark;
}
