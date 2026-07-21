package com.qianguanguan.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class BillSaveRequest {

    private Long categoryId;

    private String type;

    private BigDecimal amount;

    private Date billDate;

    private String remark;
}
