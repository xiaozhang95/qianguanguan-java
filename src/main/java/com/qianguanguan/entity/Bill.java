package com.qianguanguan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 账单实体类
 * 对应数据库表 t_bill
 */
@Data
@TableName("t_bill")
public class Bill {

    /** 主键ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 分类ID */
    private Long categoryId;

    /** 类型：INCOME-收入 / EXPENSE-支出 */
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
