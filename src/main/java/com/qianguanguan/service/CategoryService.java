package com.qianguanguan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qianguanguan.entity.Category;
import com.qianguanguan.mapper.CategoryMapper;
import com.qianguanguan.model.CategorySaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public List<Category> list(String type, Long userId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .and(w -> w.isNull(Category::getUserId).or().eq(Category::getUserId, userId))
                .orderByAsc(Category::getSort);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Category::getType, type);
        }
        return categoryMapper.selectList(wrapper);
    }

    public Category save(Long userId, CategorySaveRequest request) {
        Category category = new Category();
        category.setName(request.getName());
        category.setIcon(request.getIcon());
        category.setType(request.getType());
        category.setUserId(userId);
        category.setSort(request.getSort() != null ? request.getSort() : 0);
        category.setCreatetime(new Date());
        categoryMapper.insert(category);
        return category;
    }

    public Category update(Long userId, Long id, CategorySaveRequest request) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }
        if (category.getUserId() == null) {
            Category copy = new Category();
            copy.setName(request.getName());
            copy.setIcon(request.getIcon());
            copy.setType(request.getType());
            copy.setUserId(userId);
            copy.setSort(request.getSort());
            copy.setCreatetime(new Date());
            categoryMapper.insert(copy);
            return copy;
        }
        if (!category.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此分类");
        }
        category.setName(request.getName());
        category.setIcon(request.getIcon());
        category.setType(request.getType());
        category.setSort(request.getSort());
        categoryMapper.updateById(category);
        return category;
    }

    public void delete(Long userId, Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }
        if (category.getUserId() != null && !category.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此分类");
        }
        if (category.getUserId() == null) {
            throw new RuntimeException("系统默认分类不可删除");
        }
        categoryMapper.deleteById(id);
    }
}
