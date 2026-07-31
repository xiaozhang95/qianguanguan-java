package com.qianguanguan.service;

// --- MyBatis-Plus 查询条件构造器 ---
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// --- 分类实体 ---
import com.qianguanguan.entity.Category;
// --- 分类表 Mapper ---
import com.qianguanguan.mapper.CategoryMapper;
// --- 分类请求模型 ---
import com.qianguanguan.model.CategorySaveRequest;
// --- Lombok ---
import lombok.RequiredArgsConstructor;
// --- Spring ---
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 分类业务服务
 * <p>
 * 管理收支分类的增删改查。分类分为两类：
 * <ul>
 *   <li>系统默认分类：user_id 为 NULL，所有用户共享，不可删除</li>
 *   <li>用户自定义分类：user_id 非 NULL，用户可自由增删改</li>
 * </ul>
 * 修改系统默认分类时会自动创建用户私有副本，保留系统模板不变。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    // --- 分类表数据访问 ---
    private final CategoryMapper categoryMapper;

    /**
     * 获取分类列表
     * <p>
     * 查询条件：系统默认分类（user_id IS NULL）或当前用户的自定义分类（user_id = userId）
     * 结果按 sort 字段升序排列。
     *
     * @param type   分类类型筛选（INCOME/EXPENSE，null=全部）
     * @param userId 当前用户ID
     * @return 分类列表
     */
    public List<Category> list(String type, Long userId) {
        // 构建查询条件
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                // AND (user_id IS NULL OR user_id = userId)
                .and(w -> w.isNull(Category::getUserId)       // 系统默认分类
                        .or()                                  // 或
                        .eq(Category::getUserId, userId))      // 用户自定义分类
                .orderByAsc(Category::getSort);                // 按排序升序

        // 动态添加类型筛选
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Category::getType, type); // AND type = ?
        }
        // 执行查询
        return categoryMapper.selectList(wrapper);
    }

    /**
     * 新增用户自定义分类
     *
     * @param userId  当前用户ID
     * @param request 分类信息（名称、图标、类型、排序）
     * @return 创建成功的分类实体
     */
    public Category save(Long userId, CategorySaveRequest request) {
        // 创建分类实体并填充数据
        Category category = new Category();
        category.setName(request.getName());                     // 分类名称
        category.setIcon(request.getIcon());                     // 图标（emoji）
        category.setType(request.getType());                     // INCOME 或 EXPENSE
        category.setUserId(userId);                              // 标记为用户自定义分类
        category.setSort(request.getSort() != null ? request.getSort() : 0); // 排序序号
        category.setCreatetime(new Date());                      // 创建时间
        categoryMapper.insert(category);                         // 写入数据库
        return category;
    }

    /**
     * 更新分类
     * <p>
     * 特殊处理：若修改的是系统默认分类（userId == null），
     * 则不会直接修改原记录，而是创建一个属于当前用户的副本。
     * 这样既保护了系统模板，又让用户可以"个性化"修改。
     *
     * @param userId  当前用户ID
     * @param id      分类ID
     * @param request 新的分类信息
     * @return 更新后的分类实体
     */
    public Category update(Long userId, Long id, CategorySaveRequest request) {
        // --- 1. 查询原分类 ---
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }

        // --- 2. 系统默认分类 → 创建用户私有副本 ---
        if (category.getUserId() == null) {  // userId 为 null 说明是系统默认分类
            Category copy = new Category();  // 创建新记录（而非修改原记录）
            copy.setName(request.getName());
            copy.setIcon(request.getIcon());
            copy.setType(request.getType());
            copy.setUserId(userId);           // 标记为当前用户的私有分类
            copy.setSort(request.getSort());
            copy.setCreatetime(new Date());
            categoryMapper.insert(copy);      // 插入新记录
            return copy;                      // 返回副本
        }

        // --- 3. 用户自定义分类 → 校验归属权后直接更新 ---
        if (!category.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此分类");
        }
        category.setName(request.getName());
        category.setIcon(request.getIcon());
        category.setType(request.getType());
        category.setSort(request.getSort());
        categoryMapper.updateById(category);  // 直接更新原记录
        return category;
    }

    /**
     * 删除分类
     * <p>
     * 规则：
     * <ul>
     *   <li>系统默认分类（userId == null）：不可删除</li>
     *   <li>他人自定义分类：不可删除</li>
     *   <li>自己的自定义分类：可删除</li>
     * </ul>
     *
     * @param userId 当前用户ID
     * @param id     分类ID
     */
    public void delete(Long userId, Long id) {
        // --- 1. 查询分类 ---
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }

        // --- 2. 权限校验 ---
        // 如果是别人的自定义分类（userId 不为 null 且不等于当前用户）
        if (category.getUserId() != null && !category.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此分类");
        }
        // 系统默认分类不可删除
        if (category.getUserId() == null) {
            throw new RuntimeException("系统默认分类不可删除");
        }

        // --- 3. 物理删除 ---
        categoryMapper.deleteById(id);
    }
}
