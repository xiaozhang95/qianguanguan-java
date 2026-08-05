package com.qianguanguan.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qianguanguan.constant.JwtClaims;
import com.qianguanguan.entity.Category;
import com.qianguanguan.model.*;
import com.qianguanguan.service.BillService;
import com.qianguanguan.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 记账模块控制器
 * <p>
 * 负责接收前端请求，从 JWT 过滤器中获取当前登录用户 ID，
 * 然后委托给对应的 Service 处理业务逻辑。
 * 所有接口路径均以 /api/business 开头。
 */
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
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 请求体可为空，为空时按查询全部类型处理
        String type = body != null ? body.getType() : null;
        // 委托Service按类型查询当前用户的分类列表
        return categoryService.list(type, userId);
    }

    @Operation(summary = "新增分类")
    @PostMapping("/category/save")
    public Category categorySave(HttpServletRequest request, @RequestBody CategorySaveRequest body) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service新增分类并返回保存结果
        return categoryService.save(userId, body);
    }

    @Operation(summary = "更新分类")
    @PutMapping("/category/update/{id}")
    public Category categoryUpdate(HttpServletRequest request, @PathVariable Long id, @RequestBody CategorySaveRequest body) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service按ID更新分类并返回最新数据
        return categoryService.update(userId, id, body);
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/category/delete/{id}")
    public void categoryDelete(HttpServletRequest request, @PathVariable Long id) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service删除当前用户的指定分类
        categoryService.delete(userId, id);
    }

    @Operation(summary = "账单列表")
    @PostMapping("/bill/list")
    public IPage<BillVO> billList(HttpServletRequest request, @RequestBody BillListRequest body) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service按筛选条件分页查询账单
        return billService.list(
                // 当前登录用户ID，用于数据隔离
                userId,
                // 账单类型筛选（INCOME/EXPENSE）
                body.getType(),
                // 分类 ID 筛选
                body.getCategoryId(),
                // 开始日期（yyyy-MM-dd）
                body.getStartDate(),
                // 结束日期（yyyy-MM-dd）
                body.getEndDate(),
                // 当前页码
                body.getPageNum(),
                // 每页条数
                body.getPageSize()
        );
    }

    @Operation(summary = "账单详情")
    @GetMapping("/bill/detail/{id}")
    public BillVO billDetail(HttpServletRequest request, @PathVariable Long id) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service按ID查询账单详情（含归属校验）
        return billService.detail(userId, id);
    }

    @Operation(summary = "新增账单")
    @PostMapping("/bill/save")
    public BillVO billSave(HttpServletRequest request, @RequestBody BillSaveRequest billRequest) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service新增账单并返回保存结果
        return billService.save(userId, billRequest);
    }

    @Operation(summary = "更新账单")
    @PutMapping("/bill/update/{id}")
    public BillVO billUpdate(HttpServletRequest request, @PathVariable Long id, @RequestBody BillSaveRequest billRequest) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service按ID更新账单并返回最新数据
        return billService.update(userId, id, billRequest);
    }

    @Operation(summary = "删除账单")
    @DeleteMapping("/bill/delete/{id}")
    public void billDelete(HttpServletRequest request, @PathVariable Long id) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service删除当前用户的指定账单
        billService.delete(userId, id);
    }

    @Operation(summary = "月度统计")
    @PostMapping("/bill/stats")
    public BillStatsVO billStats(HttpServletRequest request, @RequestBody BillStatsRequest body) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service统计该用户各类型的收支数据
        return billService.stats(
                // 当前登录用户ID，用于数据隔离
                userId,
                // 年份
                body.getYear(),
                // 月份
                body.getMonth(),
                // 日期（可选）
                body.getDay(),
                // 类型筛选（可选）
                body.getType(),
                // 分类 ID 筛选（可选）
                body.getCategoryId()
        );
    }
}
