package com.qianguanguan.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Data
@Builder
public class BillVO {

    private Long id;

    private Long userId;

    private Long categoryId;

    private String categoryName;

    private String categoryIcon;

    private String type;

    private BigDecimal amount;

    private LocalDate billDate;

    private String remark;

    private Date createtime;
}
