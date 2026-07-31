package com.qianguanguan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qianguanguan.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类 Mapper 接口
 * 继承 MyBatis-Plus BaseMapper，自动获得 CRUD 能力
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
