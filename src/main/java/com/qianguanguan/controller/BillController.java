package com.qianguanguan.controller;

import com.qianguanguan.constant.JwtClaimKey;
import com.qianguanguan.entity.Category;
import com.qianguanguan.model.BillVO;
import com.qianguanguan.model.CategorySaveRequest;
import com.qianguanguan.model.CategoryListRequest;
import com.qianguanguan.model.BillSaveRequest;
import com.qianguanguan.model.BillListRequest;
import com.qianguanguan.model.BillStatsRequest;
import com.qianguanguan.model.BillStatsVO;
import com.qianguanguan.service.BillService;
import com.qianguanguan.service.CategoryService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "记账模块", description = "分类管理、账单记录、月度统计相关接口")
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;
    private final CategoryService categoryService;

    @Operation(summary = "获取分类列表")
    @PostMapping("/category/list")
    public List<Category> categoryList(@RequestBody(required = false) CategoryListRequest request) {
        String type = request != null ? request.getType() : null;
        return categoryService.list(type);
    }

    @Operation(summary = "新增分类")
    @PostMapping("/category")
    public Category categorySave(@RequestBody CategorySaveRequest request) {
        return categoryService.save(request);
    }

    @Operation(summary = "更新分类")
    @PutMapping("/category/{id}")
    public Category categoryUpdate(@PathVariable Long id, @RequestBody CategorySaveRequest request) {
        return categoryService.update(id, request);
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/category/{id}")
    public void categoryDelete(@PathVariable Long id) {
        categoryService.delete(id);
    }

    @Operation(summary = "账单列表")
    @PostMapping("/bill/list")
    public IPage<BillVO> billList(HttpServletRequest request, @RequestBody BillListRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaimKey.ID.getKey());
        return billService.list(userId, body.getType(), body.getCategoryId(),
                body.getStartDate(), body.getEndDate(),
                body.getPageNum(), body.getPageSize());
    }

    @Operation(summary = "新增账单")
    @PostMapping("/bill")
    public BillVO billSave(HttpServletRequest request, @RequestBody BillSaveRequest billRequest) {
        Long userId = (Long) request.getAttribute(JwtClaimKey.ID.getKey());
        return billService.save(userId, billRequest);
    }

    @Operation(summary = "更新账单")
    @PutMapping("/bill/{id}")
    public BillVO billUpdate(HttpServletRequest request, @PathVariable Long id, @RequestBody BillSaveRequest billRequest) {
        Long userId = (Long) request.getAttribute(JwtClaimKey.ID.getKey());
        return billService.update(userId, id, billRequest);
    }

    @Operation(summary = "删除账单")
    @DeleteMapping("/bill/{id}")
    public void billDelete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute(JwtClaimKey.ID.getKey());
        billService.delete(userId, id);
    }

    @Operation(summary = "月度统计")
    @PostMapping("/bill/stats")
    public BillStatsVO billStats(HttpServletRequest request, @RequestBody BillStatsRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaimKey.ID.getKey());
        return billService.stats(userId, body.getYear(), body.getMonth());
    }
}
