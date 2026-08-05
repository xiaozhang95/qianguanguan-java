package com.qianguanguan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qianguanguan.constant.BillType;
import com.qianguanguan.entity.Bill;
import com.qianguanguan.entity.Category;
import com.qianguanguan.mapper.BillMapper;
import com.qianguanguan.mapper.CategoryMapper;
import com.qianguanguan.model.BillSaveRequest;
import com.qianguanguan.model.BillStatsVO;
import com.qianguanguan.model.BillVO;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 账单业务服务
 * <p>
 * 处理账单的增删改查和统计分析，所有操作均绑定当前用户以保证数据隔离。
 */
@Service
@RequiredArgsConstructor
public class BillService {

    private final BillMapper billMapper;
    private final CategoryMapper categoryMapper;

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
    public IPage<BillVO> list(Long userId, String type, Long categoryId, String startDate, String endDate, Integer pageNum, Integer pageSize) {
        // --- 1. 构建查询条件 ---
        // 类型安全的查询构造器：lambda 引用字段名
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                // 必须条件：只查当前用户账单，保证数据隔离
                .eq(Bill::getUserId, userId);

        // 动态添加筛选条件：仅当参数非空时才生效
        // 类型参数非空时才按类型筛选
        if (type != null && !type.isEmpty()) {
            // 按类型筛选（INCOME/EXPENSE）
            wrapper.eq(Bill::getType, type);
        }
        // 分类ID 非空时才按分类筛选
        if (categoryId != null) {
            // 按分类ID 筛选
            wrapper.eq(Bill::getCategoryId, categoryId);
        }
        // 开始日期非空时才加下界
        if (startDate != null && !startDate.isEmpty()) {
            // 日期下界：账单日期 >= 开始日期
            wrapper.ge(Bill::getBillDate, startDate);
        }
        // 结束日期非空时才加上界
        if (endDate != null && !endDate.isEmpty()) {
            // 日期上界：账单日期 <= 结束日期
            wrapper.le(Bill::getBillDate, endDate);
        }
        // 排序：账单日期优先、创建时间其次，均降序（最新账单在前）
        wrapper.orderByDesc(Bill::getBillDate).orderByDesc(Bill::getCreatetime);

        // --- 2. 分页查询 ---
        // 页码/每页条数为 null 时回退到默认值（第 1 页 / 每页 10 条）
        // 构建分页参数对象
        Page<Bill> page = new Page<>(
                // 页码：null 时取第 1 页
                pageNum != null ? pageNum : 1,
                // 每页条数：null 时取 10 条
                pageSize != null ? pageSize : 10
        );
        // 执行分页查询，返回含总记录数的分页结果
        IPage<Bill> billPage = billMapper.selectPage(page, wrapper);

        // --- 3. 批量查询分类信息（避免 N+1 问题） ---
        // 收集本页账单的分类ID 并去重，再一次性批量查询
        // 对当前页账单记录做流式处理
        List<Long> categoryIds = billPage.getRecords().stream()
                // 提取每条账单的 categoryId
                .map(Bill::getCategoryId)
                // 去重，避免重复查询
                .distinct()
                // 收集为 List
                .collect(Collectors.toList());
        // 用分类ID 集合批量查分类，构建 分类ID->分类对象 映射，供后续快速查找
        // 分类ID 为空时走空分支
        Map<Long, Category> categoryMap = categoryIds.isEmpty()
                // 空列表返回空 Map（Java 9+ 不可变 Map）
                ? Map.of()
                // MyBatis-Plus 按主键批量查询
                : categoryMapper.selectBatchIds(categoryIds)
                // 对查询结果流式处理
                  .stream()
                // 转为 分类ID->分类对象 Map
                  .collect(Collectors.toMap(Category::getId, c -> c));

        // --- 4. 将 Bill 实体转换为 BillVO（视图对象） ---
        // 遍历账单，逐条转为 VO
        List<BillVO> voList = billPage.getRecords().stream().map(bill -> {
            // 从 Map 中取该账单的分类（可能为 null）
            Category cat = categoryMap.get(bill.getCategoryId());
            // 用 Builder 模式装配 VO
            return BillVO.builder()
                    // 账单ID
                    .id(bill.getId())
                    // 所属用户ID
                    .userId(bill.getUserId())
                    // 分类ID
                    .categoryId(bill.getCategoryId())
                    // 分类名，缺失时兜底"未知"
                    .categoryName(cat != null ? cat.getName() : "未知")
                    // 分类图标，缺失时用 ❓ 占位
                    .categoryIcon(cat != null ? cat.getIcon() : "❓")
                    // 账单类型
                    .type(bill.getType())
                    // 金额
                    .amount(bill.getAmount())
                    // 账单日期
                    .billDate(bill.getBillDate())
                    // 备注
                    .remark(bill.getRemark())
                    // 创建时间
                    .createtime(bill.getCreatetime())
                    .build();
            // 收集转换后的 VO 列表
        }).collect(Collectors.toList());

        // --- 5. 构建并返回分页 VO ---
        // 构建分页 VO
        Page<BillVO> voPage = new Page<>(
                // 当前页码
                billPage.getCurrent(),
                // 每页条数
                billPage.getSize(),
                // 总记录数
                billPage.getTotal()
        );
        // 写入转换后的 VO 列表
        voPage.setRecords(voList);
        // 返回分页结果
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
        // 新建账单实体，逐字段填充
        Bill bill = new Bill();
        // 绑定当前用户，保证数据归属
        bill.setUserId(userId);
        // 分类ID
        bill.setCategoryId(request.getCategoryId());
        // 账单类型：INCOME / EXPENSE
        bill.setType(request.getType());
        // 金额（BigDecimal 精确计算）
        bill.setAmount(request.getAmount());
        // 账单日期
        bill.setBillDate(request.getBillDate());
        // 备注
        bill.setRemark(request.getRemark());
        // 创建时间 = 当前时间
        bill.setCreatetime(new Date());
        // MyBatis-Plus 插入数据库
        billMapper.insert(bill);

        // --- 2. 构建 VO 返回 ---
        // 复用公共转换方法，附带分类名称和图标
        return toVO(bill);
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
        // 按主键查原账单
        Bill bill = billMapper.selectById(id);
        // 账单不存在时直接报错
        if (bill == null) {
            // 账单ID无效
            throw new RuntimeException("账单不存在");
        }
        // --- 2. 校验归属权 ---
        // 非本人账单，拒绝操作
        if (!bill.getUserId().equals(userId)) {
            // 不是当前用户的账单
            throw new RuntimeException("无权操作此账单");
        }
        // --- 3. 更新字段 ---
        // 更新分类ID
        bill.setCategoryId(request.getCategoryId());
        // 更新类型
        bill.setType(request.getType());
        // 更新金额
        bill.setAmount(request.getAmount());
        // 更新账单日期
        bill.setBillDate(request.getBillDate());
        // 更新备注
        bill.setRemark(request.getRemark());
        // 按主键更新整行
        billMapper.updateById(bill);

        // --- 4. 构建 VO 返回 ---
        // 复用公共转换方法，附带分类名称和图标
        return toVO(bill);
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
        // 按主键查原账单
        Bill bill = billMapper.selectById(id);
        // 账单不存在时直接报错
        if (bill == null) {
            throw new RuntimeException("账单不存在");
        }
        // --- 2. 校验归属权 ---
        // 非本人账单，拒绝操作
        if (!bill.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此账单");
        }
        // --- 3. 物理删除 ---
        // 从数据库删除该账单记录
        billMapper.deleteById(id);
    }

    /**
     * 查询账单详情（仅限本人账单）
     * <p>
     * 按主键查询账单并校验归属权，供编辑页回显使用，避免前端全量拉取列表再内存过滤。
     *
     * @param userId 当前用户ID
     * @param id     账单ID
     * @return 账单 VO
     * @throws RuntimeException 账单不存在或无权操作
     */
    public BillVO detail(Long userId, Long id) {
        // --- 1. 查询原账单 ---
        // 按主键查原账单
        Bill bill = billMapper.selectById(id);
        // 账单不存在时直接报错
        if (bill == null) {
            // 账单ID无效
            throw new RuntimeException("账单不存在");
        }
        // --- 2. 校验归属权 ---
        // 非本人账单，拒绝操作
        if (!bill.getUserId().equals(userId)) {
            // 不是当前用户的账单
            throw new RuntimeException("无权操作此账单");
        }
        // --- 3. 构建 VO 返回 ---
        // 复用公共转换方法，附带分类名称和图标
        return toVO(bill);
    }

    /**
     * 账单实体转 VO（附带分类名称/图标）
     *
     * @param bill 账单实体
     * @return 账单 VO
     */
    private BillVO toVO(Bill bill) {
        // 按分类ID 查分类实体
        Category category = categoryMapper.selectById(bill.getCategoryId());
        // 构建返回 VO
        return BillVO.builder()
                // 账单ID
                .id(bill.getId())
                // 所属用户ID
                .userId(bill.getUserId())
                // 分类ID
                .categoryId(bill.getCategoryId())
                // 分类名，缺失时兜底"未知"
                .categoryName(category != null ? category.getName() : "未知")
                // 分类图标，缺失时用 ❓ 占位
                .categoryIcon(category != null ? category.getIcon() : "❓")
                // 账单类型
                .type(bill.getType())
                // 金额
                .amount(bill.getAmount())
                // 账单日期
                .billDate(bill.getBillDate())
                // 备注
                .remark(bill.getRemark())
                // 创建时间
                .createtime(bill.getCreatetime())
                .build();
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
    public BillStatsVO stats(Long userId, String year, String month, String day, String type, Long categoryId) {
        // --- 1. 构建日期前缀（用于 SQL LIKE 查询） ---
        // 统计区间的日期前缀，如 "2026-07" 或 "2026-07-31"
        String prefix = getString(year, month, day);

        // --- 2. 构建查询条件 ---
        // 类型安全的查询条件构造器
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                // 必须：只查当前用户
                .eq(Bill::getUserId, userId)
                // LIKE 'prefix%'：前缀匹配日期区间
                .likeRight(Bill::getBillDate, prefix);

        // 类型参数非空时才筛类型
        if (type != null && !type.isEmpty()) {
            // 按类型筛选
            wrapper.eq(Bill::getType, type);
        }
        // 分类参数非空时才筛分类
        if (categoryId != null) {
            // 按分类ID 筛选
            wrapper.eq(Bill::getCategoryId, categoryId);
        }

        // --- 3. 查询符合条件的账单列表 ---
        // 查询全部符合条件的账单（不分页）
        List<Bill> bills = billMapper.selectList(wrapper);

        // --- 4. Stream 流式计算统计 ---
        // 计算总收入：过滤 INCOME 类型的账单后累加金额
        // 对账单列表流式处理
        BigDecimal totalIncome = bills.stream()
                // 只保留收入类型
                .filter(b -> BillType.INCOME.equals(b.getType()))
                // 提取金额
                .map(Bill::getAmount)
                // 累加求和，初始值为 0
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算总支出：过滤 EXPENSE 类型的账单后累加金额
        // 对账单列表流式处理
        BigDecimal totalExpense = bills.stream()
                // 只保留支出类型
                .filter(b -> BillType.EXPENSE.equals(b.getType()))
                // 提取金额
                .map(Bill::getAmount)
                // 累加求和，初始值为 0
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- 5. 构建统计 VO 返回 ---
        // 构建统计结果 VO
        return BillStatsVO.builder()
                // 总收入
                .totalIncome(totalIncome)
                // 总支出
                .totalExpense(totalExpense)
                // 结余 = 收入 - 支出
                .balance(totalIncome.subtract(totalExpense))
                .build();
    }

    private static @NonNull String getString(String year, String month, String day) {
        String prefix;
        // 月份补零为两位数，如 "7" -> "07"
        String s = month.length() == 1 ? "0" + month : month;
        // 传入了日，则按日统计
        if (day != null && !day.isEmpty()) {
            // 按日统计：日期补零确保两位数，如 "7" -> "07"
            // 拼接：年-
            prefix = year + "-"
                    // 月-
                    + s + "-"
                    // 日（不足两位补零）
                    + (day.length() == 1 ? "0" + day : day);
        } else {
            // 按月统计
            // 拼接为 年-月
            prefix = year + "-" + s;
        }
        return prefix;
    }
}
