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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillMapper billMapper;
    private final CategoryMapper categoryMapper;

    public IPage<BillVO> list(Long userId, String type, Long categoryId,
                              String startDate, String endDate,
                              Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId);

        if (type != null && !type.isEmpty()) {
            wrapper.eq(Bill::getType, type);
        }
        if (categoryId != null) {
            wrapper.eq(Bill::getCategoryId, categoryId);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(Bill::getBillDate, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(Bill::getBillDate, endDate);
        }
        wrapper.orderByDesc(Bill::getBillDate).orderByDesc(Bill::getCreatetime);

        Page<Bill> page = new Page<>(pageNum != null ? pageNum : 1, pageSize != null ? pageSize : 10);
        IPage<Bill> billPage = billMapper.selectPage(page, wrapper);

        List<Long> categoryIds = billPage.getRecords().stream()
                .map(Bill::getCategoryId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Category> categoryMap = categoryIds.isEmpty()
                ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, c -> c));

        List<BillVO> voList = billPage.getRecords().stream().map(bill -> {
            Category cat = categoryMap.get(bill.getCategoryId());
            return BillVO.builder()
                    .id(bill.getId())
                    .userId(bill.getUserId())
                    .categoryId(bill.getCategoryId())
                    .categoryName(cat != null ? cat.getName() : "未知")
                    .categoryIcon(cat != null ? cat.getIcon() : "❓")
                    .type(bill.getType())
                    .amount(bill.getAmount())
                    .billDate(bill.getBillDate())
                    .remark(bill.getRemark())
                    .createtime(bill.getCreatetime())
                    .build();
        }).collect(Collectors.toList());

        Page<BillVO> voPage = new Page<>(billPage.getCurrent(), billPage.getSize(), billPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    public BillVO save(Long userId, BillSaveRequest request) {
        Bill bill = new Bill();
        bill.setUserId(userId);
        bill.setCategoryId(request.getCategoryId());
        bill.setType(request.getType());
        bill.setAmount(request.getAmount());
        bill.setBillDate(request.getBillDate());
        bill.setRemark(request.getRemark());
        bill.setCreatetime(new Date());
        billMapper.insert(bill);

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

    public BillVO update(Long userId, Long id, BillSaveRequest request) {
        Bill bill = billMapper.selectById(id);
        if (bill == null) {
            throw new RuntimeException("账单不存在");
        }
        if (!bill.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此账单");
        }
        bill.setCategoryId(request.getCategoryId());
        bill.setType(request.getType());
        bill.setAmount(request.getAmount());
        bill.setBillDate(request.getBillDate());
        bill.setRemark(request.getRemark());
        billMapper.updateById(bill);

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

    public void delete(Long userId, Long id) {
        Bill bill = billMapper.selectById(id);
        if (bill == null) {
            throw new RuntimeException("账单不存在");
        }
        if (!bill.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此账单");
        }
        billMapper.deleteById(id);
    }

    public BillStatsVO stats(Long userId, String year, String month, String day) {
        String prefix;
        if (day != null && !day.isEmpty()) {
            prefix = year + "-" + (month.length() == 1 ? "0" + month : month) + "-" + (day.length() == 1 ? "0" + day : day);
        } else {
            prefix = year + "-" + (month.length() == 1 ? "0" + month : month);
        }

        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)
                .likeRight(Bill::getBillDate, prefix);

        List<Bill> bills = billMapper.selectList(wrapper);

        BigDecimal totalIncome = bills.stream()
                .filter(b -> BillType.INCOME.equals(b.getType()))
                .map(Bill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = bills.stream()
                .filter(b -> BillType.EXPENSE.equals(b.getType()))
                .map(Bill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BillStatsVO.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(totalIncome.subtract(totalExpense))
                .build();
    }
}
