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

/**
 * 记账模块控制器
 * <p>
 * 负责接收前端请求，从 JWT 过滤器中获取当前登录用户 ID，
 * 然后委托给对应的 Service 处理业务逻辑。
 * 所有接口路径均以 /api/business 开头。
 */
// Swagger 分组标签
@Tag(name = "记账模块", description = "分类管理、账单记录、月度统计相关接口")
// 标识这是一个 REST 控制器，所有方法自动返回 JSON
@RestController
// 统一的基础请求路径
@RequestMapping("/api/business")
// Lombok：自动生成构造器，注入所有 final 字段
@RequiredArgsConstructor
public class BillController {

    // --- 通过构造器注入 Service（@RequiredArgsConstructor 自动处理） ---
    // 账单业务服务
    private final BillService billService;
    // 分类业务服务
    private final CategoryService categoryService;

    // ======================== 分类管理 ========================

    // Swagger 接口说明
    @Operation(summary = "获取分类列表")
    // POST /api/business/category/list
    @PostMapping("/category/list")
    public List<Category> categoryList(
            // 从 JWT 过滤器获取用户信息
            HttpServletRequest request,
            // 请求体（可选），含筛选类型
            @RequestBody(required = false) CategoryListRequest body
    ) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 请求体可为空，为空时按查询全部类型处理
        String type = body != null ? body.getType() : null;
        // 委托Service按类型查询当前用户的分类列表
        return categoryService.list(type, userId);
    }

    // Swagger 接口说明
    @Operation(summary = "新增分类")
    // POST /api/business/category/save
    @PostMapping("/category/save")
    public Category categorySave(
            // 从 JWT 过滤器获取用户信息
            HttpServletRequest request,
            // 请求体：分类名称、图标、类型、排序
            @RequestBody CategorySaveRequest body
    ) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service新增分类并返回保存结果
        return categoryService.save(userId, body);
    }

    // Swagger 接口说明
    @Operation(summary = "更新分类")
    // PUT /api/business/category/update/{id}
    @PutMapping("/category/update/{id}")
    public Category categoryUpdate(
            // 从 JWT 过滤器获取用户信息
            HttpServletRequest request,
            // URL 路径中的分类 ID
            @PathVariable Long id,
            // 请求体：新的分类信息
            @RequestBody CategorySaveRequest body
    ) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service按ID更新分类并返回最新数据
        return categoryService.update(userId, id, body);
    }

    // Swagger 接口说明
    @Operation(summary = "删除分类")
    // DELETE /api/business/category/delete/{id}
    @DeleteMapping("/category/delete/{id}")
    public void categoryDelete(
            // 从 JWT 过滤器获取用户信息
            HttpServletRequest request,
            // URL 路径中的分类 ID
            @PathVariable Long id
    ) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service删除当前用户的指定分类
        categoryService.delete(userId, id);
    }

    // ======================== 账单管理 ========================

    // Swagger 接口说明
    @Operation(summary = "账单列表")
    // POST /api/business/bill/list
    @PostMapping("/bill/list")
    public IPage<BillVO> billList(
            // 从 JWT 过滤器获取用户信息
            HttpServletRequest request,
            // 请求体：类型、分类、日期范围、分页参数
            @RequestBody BillListRequest body
    ) {
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

    // Swagger 接口说明
    @Operation(summary = "新增账单")
    // POST /api/business/bill/save
    @PostMapping("/bill/save")
    public BillVO billSave(
            // 从 JWT 过滤器获取用户信息
            HttpServletRequest request,
            // 请求体：分类、类型、金额、日期、备注
            @RequestBody BillSaveRequest billRequest
    ) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service新增账单并返回保存结果
        return billService.save(userId, billRequest);
    }

    // Swagger 接口说明
    @Operation(summary = "更新账单")
    // PUT /api/business/bill/update/{id}
    @PutMapping("/bill/update/{id}")
    public BillVO billUpdate(
            // 从 JWT 过滤器获取用户信息
            HttpServletRequest request,
            // URL 路径中的账单 ID
            @PathVariable Long id,
            // 请求体：新的账单信息
            @RequestBody BillSaveRequest billRequest
    ) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service按ID更新账单并返回最新数据
        return billService.update(userId, id, billRequest);
    }

    // Swagger 接口说明
    @Operation(summary = "删除账单")
    // DELETE /api/business/bill/delete/{id}
    @DeleteMapping("/bill/delete/{id}")
    public void billDelete(
            // 从 JWT 过滤器获取用户信息
            HttpServletRequest request,
            // URL 路径中的账单 ID
            @PathVariable Long id
    ) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service删除当前用户的指定账单
        billService.delete(userId, id);
    }

    // ======================== 统计分析 ========================

    // Swagger 接口说明
    @Operation(summary = "月度统计")
    // POST /api/business/bill/stats
    @PostMapping("/bill/stats")
    public BillStatsVO billStats(
            // 从 JWT 过滤器获取用户信息
            HttpServletRequest request,
            // 请求体：年、月、日、类型、分类ID
            @RequestBody BillStatsRequest body
    ) {
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
