package com.qianguanguan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("t_bill")
public class Bill {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long categoryId;

    private String type;

    private BigDecimal amount;

    private Date billDate;

    private String remark;

    private Date createtime;
}
