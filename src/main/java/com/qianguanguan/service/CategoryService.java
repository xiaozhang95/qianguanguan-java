package com.qianguanguan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qianguanguan.entity.Category;
import com.qianguanguan.mapper.CategoryMapper;
import com.qianguanguan.model.CategorySaveRequest;
import lombok.RequiredArgsConstructor;
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
        // 类型安全的查询条件构造器
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                // AND (user_id IS NULL OR user_id = userId)
                // 系统默认分类（user_id 为空）
                .and(w -> w.isNull(Category::getUserId)
                        // 或
                        .or()
                        // 当前用户的自定义分类
                        .eq(Category::getUserId, userId))
                // 按 sort 字段升序排列
                .orderByAsc(Category::getSort);

        // 类型参数非空时才按类型筛选
        if (type != null && !type.isEmpty()) {
            // AND type = ?
            wrapper.eq(Category::getType, type);
        }
        // 执行查询并返回分类列表
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
        // 新建分类实体，逐字段填充
        Category category = new Category();
        // 分类名称
        category.setName(request.getName());
        // 图标（emoji）
        category.setIcon(request.getIcon());
        // 类型：INCOME 或 EXPENSE
        category.setType(request.getType());
        // 标记为用户自定义分类
        category.setUserId(userId);
        // 排序序号，null 时取 0
        category.setSort(request.getSort() != null ? request.getSort() : 0);
        // 创建时间：当前时间
        category.setCreatetime(new Date());
        // 写入数据库
        categoryMapper.insert(category);
        // 返回带自增 ID 的实体
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
        // 分类不存在时直接报错
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }

        // --- 2. 系统默认分类 → 创建用户私有副本 ---
        // userId 为 null 说明是系统默认分类
        if (category.getUserId() == null) {
            // 新建副本记录（不修改系统模板）
            Category copy = new Category();
            // 分类名称
            copy.setName(request.getName());
            // 图标
            copy.setIcon(request.getIcon());
            // 类型
            copy.setType(request.getType());
            // 归属当前用户
            copy.setUserId(userId);
            // 排序
            copy.setSort(request.getSort());
            // 创建时间
            copy.setCreatetime(new Date());
            // 插入新记录
            categoryMapper.insert(copy);
            // 返回副本
            return copy;
        }

        // --- 3. 用户自定义分类 → 校验归属权后直接更新 ---
        // 非本人分类拒绝修改
        if (!category.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此分类");
        }
        // 更新分类名称
        category.setName(request.getName());
        // 更新图标
        category.setIcon(request.getIcon());
        // 更新类型
        category.setType(request.getType());
        // 更新排序
        category.setSort(request.getSort());
        // 按主键更新记录
        categoryMapper.updateById(category);
        // 返回更新后的分类
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
        // 分类不存在时直接报错
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }

        // --- 2. 权限校验 ---
        // 他人自定义分类无权删除
        if (category.getUserId() != null && !category.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此分类");
        }
        // 系统默认分类受保护，不可删除
        if (category.getUserId() == null) {
            throw new RuntimeException("系统默认分类不可删除");
        }

        // --- 3. 物理删除 ---
        // 从数据库删除该分类记录
        categoryMapper.deleteById(id);
    }
}
