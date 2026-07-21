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

    public List<Category> list(String type) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Category::getType, type);
        }
        return categoryMapper.selectList(wrapper);
    }

    public Category save(CategorySaveRequest request) {
        Category category = new Category();
        category.setName(request.getName());
        category.setIcon(request.getIcon());
        category.setType(request.getType());
        category.setSort(request.getSort() != null ? request.getSort() : 0);
        category.setCreatetime(new Date());
        categoryMapper.insert(category);
        return category;
    }

    public Category update(Long id, CategorySaveRequest request) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }
        category.setName(request.getName());
        category.setIcon(request.getIcon());
        category.setType(request.getType());
        category.setSort(request.getSort());
        categoryMapper.updateById(category);
        return category;
    }

    public void delete(Long id) {
        categoryMapper.deleteById(id);
    }
}
