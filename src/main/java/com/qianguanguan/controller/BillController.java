package com.qianguanguan.controller;

// --- 导入常量：JWT Claims 字段名（id、role 等） ---
import com.qianguanguan.constant.JwtClaims;
// --- 导入实体类 ---
import com.qianguanguan.entity.Category;
// --- 导入 VO/请求模型 ---
import com.qianguanguan.model.BillVO;
import com.qianguanguan.model.CategorySaveRequest;
import com.qianguanguan.model.CategoryListRequest;
import com.qianguanguan.model.BillSaveRequest;
import com.qianguanguan.model.BillListRequest;
import com.qianguanguan.model.BillStatsRequest;
import com.qianguanguan.model.BillStatsVO;
// --- 导入 Service 层 ---
import com.qianguanguan.service.BillService;
import com.qianguanguan.service.CategoryService;
// --- MyBatis-Plus 分页对象 ---
import com.baomidou.mybatisplus.core.metadata.IPage;
// --- Swagger 注解 ---
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
// --- Jakarta Servlet：获取请求对象 ---
import jakarta.servlet.http.HttpServletRequest;
// --- Lombok：自动生成含 final 字段的构造器 ---
import lombok.RequiredArgsConstructor;
// --- Spring MVC 注解 ---
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 记账模块控制器
 * <p>
 * 负责接收前端请求，从 JWT 过滤器中获取当前登录用户 ID，
 * 然后委托给对应的 Service 处理业务逻辑。
 * 所有接口路径均以 /api/business 开头。
 */
@Tag(name = "记账模块", description = "分类管理、账单记录、月度统计相关接口") // Swagger 分组标签
@RestController                 // 标识这是一个 REST 控制器，所有方法自动返回 JSON
@RequestMapping("/api/business") // 统一的基础请求路径
@RequiredArgsConstructor        // Lombok：自动生成构造器，注入所有 final 字段
public class BillController {

    // --- 通过构造器注入 Service（@RequiredArgsConstructor 自动处理） ---
    private final BillService billService;           // 账单业务服务
    private final CategoryService categoryService;   // 分类业务服务

    // ======================== 分类管理 ========================

    @Operation(summary = "获取分类列表")  // Swagger 接口说明
    @PostMapping("/category/list")       // POST /api/business/category/list
    public List<Category> categoryList(
            HttpServletRequest request,                          // 从 JWT 过滤器获取用户信息
            @RequestBody(required = false) CategoryListRequest body // 请求体（可选），含筛选类型
    ) {
        // 从 Request 属性中取出当前登录用户 ID（由 JwtAuthenticationFilter 写入）
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 如果传了 type 参数则使用，否则传 null 表示查询全部类型
        String type = body != null ? body.getType() : null;
        // 委托 CategoryService 查询分类列表
        return categoryService.list(type, userId);
    }

    @Operation(summary = "新增分类")  // Swagger 接口说明
    @PostMapping("/category/save")   // POST /api/business/category/save
    public Category categorySave(
            HttpServletRequest request,          // 从 JWT 过滤器获取用户信息
            @RequestBody CategorySaveRequest body // 请求体：分类名称、图标、类型、排序
    ) {
        // 取出当前登录用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 CategoryService 新增分类
        return categoryService.save(userId, body);
    }

    @Operation(summary = "更新分类")  // Swagger 接口说明
    @PutMapping("/category/update/{id}")  // PUT /api/business/category/update/{id}
    public Category categoryUpdate(
            HttpServletRequest request,           // 从 JWT 过滤器获取用户信息
            @PathVariable Long id,                // URL 路径中的分类 ID
            @RequestBody CategorySaveRequest body // 请求体：新的分类信息
    ) {
        // 取出当前登录用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 CategoryService 更新分类
        return categoryService.update(userId, id, body);
    }

    @Operation(summary = "删除分类")  // Swagger 接口说明
    @DeleteMapping("/category/delete/{id}")  // DELETE /api/business/category/delete/{id}
    public void categoryDelete(
            HttpServletRequest request, // 从 JWT 过滤器获取用户信息
            @PathVariable Long id       // URL 路径中的分类 ID
    ) {
        // 取出当前登录用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 CategoryService 删除分类
        categoryService.delete(userId, id);
    }

    // ======================== 账单管理 ========================

    @Operation(summary = "账单列表")  // Swagger 接口说明
    @PostMapping("/bill/list")       // POST /api/business/bill/list
    public IPage<BillVO> billList(
            HttpServletRequest request,  // 从 JWT 过滤器获取用户信息
            @RequestBody BillListRequest body // 请求体：类型、分类、日期范围、分页参数
    ) {
        // 取出当前登录用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 BillService 分页查询账单，传入筛选条件
        return billService.list(
                userId,
                body.getType(),       // 账单类型筛选（INCOME/EXPENSE）
                body.getCategoryId(), // 分类 ID 筛选
                body.getStartDate(),  // 开始日期（yyyy-MM-dd）
                body.getEndDate(),    // 结束日期（yyyy-MM-dd）
                body.getPageNum(),    // 当前页码
                body.getPageSize()    // 每页条数
        );
    }

    @Operation(summary = "新增账单")  // Swagger 接口说明
    @PostMapping("/bill/save")       // POST /api/business/bill/save
    public BillVO billSave(
            HttpServletRequest request,      // 从 JWT 过滤器获取用户信息
            @RequestBody BillSaveRequest billRequest // 请求体：分类、类型、金额、日期、备注
    ) {
        // 取出当前登录用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 BillService 新增账单
        return billService.save(userId, billRequest);
    }

    @Operation(summary = "更新账单")  // Swagger 接口说明
    @PutMapping("/bill/update/{id}")  // PUT /api/business/bill/update/{id}
    public BillVO billUpdate(
            HttpServletRequest request,       // 从 JWT 过滤器获取用户信息
            @PathVariable Long id,            // URL 路径中的账单 ID
            @RequestBody BillSaveRequest billRequest // 请求体：新的账单信息
    ) {
        // 取出当前登录用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 BillService 更新账单
        return billService.update(userId, id, billRequest);
    }

    @Operation(summary = "删除账单")  // Swagger 接口说明
    @DeleteMapping("/bill/delete/{id}")  // DELETE /api/business/bill/delete/{id}
    public void billDelete(
            HttpServletRequest request, // 从 JWT 过滤器获取用户信息
            @PathVariable Long id       // URL 路径中的账单 ID
    ) {
        // 取出当前登录用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 BillService 删除账单
        billService.delete(userId, id);
    }

    // ======================== 统计分析 ========================

    @Operation(summary = "月度统计")  // Swagger 接口说明
    @PostMapping("/bill/stats")      // POST /api/business/bill/stats
    public BillStatsVO billStats(
            HttpServletRequest request,    // 从 JWT 过滤器获取用户信息
            @RequestBody BillStatsRequest body // 请求体：年、月、日、类型、分类ID
    ) {
        // 取出当前登录用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 BillService 进行统计计算
        return billService.stats(
                userId,
                body.getYear(),       // 年份
                body.getMonth(),      // 月份
                body.getDay(),        // 日期（可选）
                body.getType(),       // 类型筛选（可选）
                body.getCategoryId()  // 分类 ID 筛选（可选）
        );
    }
}
