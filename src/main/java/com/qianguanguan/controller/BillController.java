package com.qianguanguan.controller;

import com.qianguanguan.constant.JwtClaims;
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
    public List<Category> categoryList(HttpServletRequest request, @RequestBody(required = false) CategoryListRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        String type = body != null ? body.getType() : null;
        return categoryService.list(type, userId);
    }

    @Operation(summary = "新增分类")
    @PostMapping("/category/save")
    public Category categorySave(HttpServletRequest request, @RequestBody CategorySaveRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        return categoryService.save(userId, body);
    }

    @Operation(summary = "更新分类")
    @PutMapping("/category/update/{id}")
    public Category categoryUpdate(HttpServletRequest request, @PathVariable Long id, @RequestBody CategorySaveRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        return categoryService.update(userId, id, body);
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/category/delete/{id}")
    public void categoryDelete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        categoryService.delete(userId, id);
    }

    @Operation(summary = "账单列表")
    @PostMapping("/bill/list")
    public IPage<BillVO> billList(HttpServletRequest request, @RequestBody BillListRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        return billService.list(userId, body.getType(), body.getCategoryId(),
                body.getStartDate(), body.getEndDate(),
                body.getPageNum(), body.getPageSize());
    }

    @Operation(summary = "新增账单")
    @PostMapping("/bill/save")
    public BillVO billSave(HttpServletRequest request, @RequestBody BillSaveRequest billRequest) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        return billService.save(userId, billRequest);
    }

    @Operation(summary = "更新账单")
    @PutMapping("/bill/update/{id}")
    public BillVO billUpdate(HttpServletRequest request, @PathVariable Long id, @RequestBody BillSaveRequest billRequest) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        return billService.update(userId, id, billRequest);
    }

    @Operation(summary = "删除账单")
    @DeleteMapping("/bill/delete/{id}")
    public void billDelete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        billService.delete(userId, id);
    }

    @Operation(summary = "月度统计")
    @PostMapping("/bill/stats")
    public BillStatsVO billStats(HttpServletRequest request, @RequestBody BillStatsRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        return billService.stats(userId, body.getYear(), body.getMonth(), body.getDay(), body.getType(), body.getCategoryId());
    }
}
