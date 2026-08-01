package com.qianguanguan.service;

// --- MyBatis-Plus 查询条件构造器 ---
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// --- MyBatis-Plus 分页接口/实现 ---
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
// --- 常量：账单类型 INCOME / EXPENSE ---
import com.qianguanguan.constant.BillType;
// --- 实体类 ---
import com.qianguanguan.entity.Bill;
import com.qianguanguan.entity.Category;
// --- Mapper 层 ---
import com.qianguanguan.mapper.BillMapper;
import com.qianguanguan.mapper.CategoryMapper;
// --- 请求/响应模型 ---
import com.qianguanguan.model.BillSaveRequest;
import com.qianguanguan.model.BillStatsVO;
import com.qianguanguan.model.BillVO;
// --- Lombok ---
import lombok.RequiredArgsConstructor;
// --- Spring ---
import org.springframework.stereotype.Service;

// --- Java 基础库 ---
import java.math.BigDecimal;   // 精确金额计算
import java.util.Date;          // 日期
import java.util.List;          // 列表
import java.util.Map;           // 映射表
import java.util.stream.Collectors; // Stream 收集器

/**
 * 账单业务服务
 * <p>
 * 处理账单的增删改查和统计分析，所有操作均绑定当前用户以保证数据隔离。
 */
@Service             // Spring Service 组件注解
@RequiredArgsConstructor // 自动生成构造器注入
public class BillService {

    // --- 数据访问层（MyBatis-Plus Mapper） ---
    private final BillMapper billMapper;         // 账单表 Mapper
    private final CategoryMapper categoryMapper; // 分类表 Mapper

    /**
     * 分页查询账单列表
     * <p>
     * 支持按类型（收入/支出）、分类ID、日期范围多条件筛选，
     * 返回带分类名称和图标的 VO 对象，方便前端直接展示。
     *
     * @param userId     当前用户ID（数据隔离）
     * @param type       账单类型筛选（INCOME/EXPENSE，null=全部）
     * @param categoryId 分类ID筛选（null=全部）
     * @param startDate  开始日期（yyyy-MM-dd，null=不限制）
     * @param endDate    结束日期（yyyy-MM-dd，null=不限制）
     * @param pageNum    页码（默认1）
     * @param pageSize   每页条数（默认10）
     * @return 分页结果，包含 BillVO 列表
     */
    public IPage<BillVO> list(Long userId, String type, Long categoryId,
                              String startDate, String endDate,
                              Integer pageNum, Integer pageSize) {
        // --- 1. 构建查询条件 ---
        // LambdaQueryWrapper：类型安全的查询构造器，使用 lambda 表达式引用字段名
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId); // 必须条件：只查当前用户的账单

        // 动态添加筛选条件：仅当参数非空时才添加
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Bill::getType, type);           // 等于：类型
        }
        if (categoryId != null) {
            wrapper.eq(Bill::getCategoryId, categoryId); // 等于：分类ID
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(Bill::getBillDate, startDate);  // 大于等于：开始日期
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(Bill::getBillDate, endDate);    // 小于等于：结束日期
        }
        // 按账单日期降序，再按创建时间降序（最新的在前）
        wrapper.orderByDesc(Bill::getBillDate).orderByDesc(Bill::getCreatetime);

        // --- 2. 分页查询 ---
        // 创建分页对象，页码/每页条数为 null 时使用默认值
        Page<Bill> page = new Page<>(
                pageNum != null ? pageNum : 1,     // 默认第1页
                pageSize != null ? pageSize : 10   // 默认每页10条
        );
        IPage<Bill> billPage = billMapper.selectPage(page, wrapper); // 执行分页查询

        // --- 3. 批量查询分类信息（避免 N+1 问题） ---
        // 从分页结果中提取所有去重的分类ID
        List<Long> categoryIds = billPage.getRecords().stream()
                .map(Bill::getCategoryId)        // 提取每条账单的 categoryId
                .distinct()                      // 去重
                .collect(Collectors.toList());   // 收集为 List
        // 批量查询分类表，构建 Map<分类ID, 分类对象> 用于快速查找
        Map<Long, Category> categoryMap = categoryIds.isEmpty()
                ? Map.of()                       // 空列表时返回空 Map（Java 9+ 不可变 Map）
                : categoryMapper.selectBatchIds(categoryIds)  // MyBatis-Plus 批量查询
                        .stream()
                        .collect(Collectors.toMap(Category::getId, c -> c)); // 转为 Map

        // --- 4. 将 Bill 实体转换为 BillVO（视图对象） ---
        List<BillVO> voList = billPage.getRecords().stream().map(bill -> {
            // 从 Map 中获取该账单对应的分类信息
            Category cat = categoryMap.get(bill.getCategoryId());
            // 使用 Builder 模式构建 VO，将分类名称和图标一并返回
            return BillVO.builder()
                    .id(bill.getId())
                    .userId(bill.getUserId())
                    .categoryId(bill.getCategoryId())
                    .categoryName(cat != null ? cat.getName() : "未知")   // 分类名
                    .categoryIcon(cat != null ? cat.getIcon() : "❓")     // 分类图标
                    .type(bill.getType())
                    .amount(bill.getAmount())
                    .billDate(bill.getBillDate())
                    .remark(bill.getRemark())
                    .createtime(bill.getCreatetime())
                    .build();
        }).collect(Collectors.toList()); // 收集转换结果

        // --- 5. 构建并返回分页 VO ---
        Page<BillVO> voPage = new Page<>(
                billPage.getCurrent(), // 当前页码
                billPage.getSize(),    // 每页条数
                billPage.getTotal()    // 总记录数
        );
        voPage.setRecords(voList);     // 设置转换后的 VO 列表
        return voPage;
    }

    /**
     * 新增账单
     * <p>
     * 创建 Bill 实体，填充请求参数和当前用户ID，写入数据库后返回带分类信息的 VO。
     *
     * @param userId  当前用户ID
     * @param request 账单请求参数（分类、类型、金额、日期、备注）
     * @return 创建成功的账单 VO
     */
    public BillVO save(Long userId, BillSaveRequest request) {
        // --- 1. 构建 Bill 实体对象 ---
        Bill bill = new Bill();
        bill.setUserId(userId);                    // 绑定当前用户
        bill.setCategoryId(request.getCategoryId()); // 分类ID
        bill.setType(request.getType());           // INCOME 或 EXPENSE
        bill.setAmount(request.getAmount());       // 金额（BigDecimal 精确）
        bill.setBillDate(request.getBillDate());   // 账单日期
        bill.setRemark(request.getRemark());       // 备注
        bill.setCreatetime(new Date());            // 创建时间 = 当前时间
        billMapper.insert(bill);                   // MyBatis-Plus 插入数据库

        // --- 2. 查询分类信息用于返回 ---
        Category category = categoryMapper.selectById(bill.getCategoryId());
        // --- 3. 构建 VO 返回 ---
        return BillVO.builder()
                .id(bill.getId())
                .userId(bill.getUserId())
                .categoryId(bill.getCategoryId())
                .categoryName(category != null ? category.getName() : "未知")
                .categoryIcon(category != null ? category.getIcon() : "❓")
                .type(bill.getType())
                .amount(bill.getAmount())
                .billDate(bill.getBillDate())
                .remark(bill.getRemark())
                .createtime(bill.getCreatetime())
                .build();
    }

    /**
     * 更新账单（仅限本人账单）
     * <p>
     * 先查询原账单是否存在，再校验归属权，然后更新字段。
     *
     * @param userId  当前用户ID
     * @param id      账单ID
     * @param request 新的账单信息
     * @return 更新后的账单 VO
     * @throws RuntimeException 账单不存在或无权操作
     */
    public BillVO update(Long userId, Long id, BillSaveRequest request) {
        // --- 1. 查询原账单 ---
        Bill bill = billMapper.selectById(id);
        if (bill == null) {
            throw new RuntimeException("账单不存在");  // 账单ID无效
        }
        // --- 2. 校验归属权 ---
        if (!bill.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此账单"); // 不是当前用户的账单
        }
        // --- 3. 更新字段 ---
        bill.setCategoryId(request.getCategoryId());
        bill.setType(request.getType());
        bill.setAmount(request.getAmount());
        bill.setBillDate(request.getBillDate());
        bill.setRemark(request.getRemark());
        billMapper.updateById(bill); // MyBatis-Plus 按主键更新

        // --- 4. 查询分类并返回 VO ---
        Category category = categoryMapper.selectById(bill.getCategoryId());
        return BillVO.builder()
                .id(bill.getId())
                .userId(bill.getUserId())
                .categoryId(bill.getCategoryId())
                .categoryName(category != null ? category.getName() : "未知")
                .categoryIcon(category != null ? category.getIcon() : "❓")
                .type(bill.getType())
                .amount(bill.getAmount())
                .billDate(bill.getBillDate())
                .remark(bill.getRemark())
                .createtime(bill.getCreatetime())
                .build();
    }

    /**
     * 删除账单（仅限本人账单）
     *
     * @param userId 当前用户ID
     * @param id     账单ID
     * @throws RuntimeException 账单不存在或无权操作
     */
    public void delete(Long userId, Long id) {
        // --- 1. 查询原账单 ---
        Bill bill = billMapper.selectById(id);
        if (bill == null) {
            throw new RuntimeException("账单不存在");
        }
        // --- 2. 校验归属权 ---
        if (!bill.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此账单");
        }
        // --- 3. 物理删除 ---
        billMapper.deleteById(id);
    }

    /**
     * 月度/日统计
     * <p>
     * 按年/月（或年/月/日）汇总指定用户的收入和支出总额，计算结余。
     * 使用 SQL LIKE 前缀匹配查询指定时间段内的所有账单。
     *
     * @param userId     当前用户ID
     * @param year       年份（如 "2026"）
     * @param month      月份（如 "7"）
     * @param day        日期（可选，如 "31"，传入则按日统计）
     * @param type       类型筛选（可选）
     * @param categoryId 分类筛选（可选）
     * @return 统计结果 VO（总收入、总支出、结余）
     */
    public BillStatsVO stats(Long userId, String year, String month,
                             String day, String type, Long categoryId) {
        // --- 1. 构建日期前缀（用于 SQL LIKE 查询） ---
        String prefix; // 如 "2026-07" 或 "2026-07-31"
        String s = month.length() == 1 ? "0" + month : month;
        if (day != null && !day.isEmpty()) {
            // 按日统计：补零确保两位数，如 "7" → "07"
            prefix = year + "-"
                    + s + "-"
                    + (day.length() == 1 ? "0" + day : day);
        } else {
            // 按月统计
            prefix = year + "-" + s;
        }

        // --- 2. 构建查询条件 ---
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)           // 必须：当前用户
                .likeRight(Bill::getBillDate, prefix); // LIKE 'prefix%'：前缀匹配

        // 动态添加可选筛选条件
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Bill::getType, type);
        }
        if (categoryId != null) {
            wrapper.eq(Bill::getCategoryId, categoryId);
        }

        // --- 3. 查询符合条件的账单列表 ---
        List<Bill> bills = billMapper.selectList(wrapper);

        // --- 4. Stream 流式计算统计 ---
        // 计算总收入：过滤 INCOME 类型的账单，累加金额
        BigDecimal totalIncome = bills.stream()
                .filter(b -> BillType.INCOME.equals(b.getType())) // 只保留收入
                .map(Bill::getAmount)                              // 提取金额
                .reduce(BigDecimal.ZERO, BigDecimal::add);         // 累加，初始值 0

        // 计算总支出：过滤 EXPENSE 类型的账单，累加金额
        BigDecimal totalExpense = bills.stream()
                .filter(b -> BillType.EXPENSE.equals(b.getType())) // 只保留支出
                .map(Bill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- 5. 构建统计 VO 返回 ---
        return BillStatsVO.builder()
                .totalIncome(totalIncome)                        // 总收入
                .totalExpense(totalExpense)                      // 总支出
                .balance(totalIncome.subtract(totalExpense))     // 结余 = 收入 - 支出
                .build();
    }
}
