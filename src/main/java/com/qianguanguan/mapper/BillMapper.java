package com.qianguanguan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qianguanguan.entity.Bill;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账单 Mapper 接口
 * 继承 MyBatis-Plus BaseMapper，自动获得 CRUD 能力
 */
@Mapper
public interface BillMapper extends BaseMapper<Bill> {
}
