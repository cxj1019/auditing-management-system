package com.accounting.firm.reimbursement.service.impl;

import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.reimbursement.dto.ExpenseCategoryRequest;
import com.accounting.firm.reimbursement.entity.ExpenseCategory;
import com.accounting.firm.reimbursement.mapper.ExpenseCategoryMapper;
import com.accounting.firm.reimbursement.service.ExpenseCategoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报销费用类别服务实现
 */
@Service
public class ExpenseCategoryServiceImpl extends ServiceImpl<ExpenseCategoryMapper, ExpenseCategory>
        implements ExpenseCategoryService {

    @Override
    public List<ExpenseCategory> listAll() {
        return lambdaQuery()
                .orderByAsc(ExpenseCategory::getSort)
                .orderByAsc(ExpenseCategory::getId)
                .list();
    }

    @Override
    public ExpenseCategory create(ExpenseCategoryRequest request) {
        requireUniqueName(request.getName(), null);
        ExpenseCategory category = new ExpenseCategory();
        copyFields(request, category);
        category.setCreateTime(LocalDateTime.now());
        save(category);
        return category;
    }

    @Override
    public ExpenseCategory update(Long id, ExpenseCategoryRequest request) {
        ExpenseCategory category = getById(id);
        if (category == null) {
            throw new BusinessException("类别不存在");
        }
        requireUniqueName(request.getName(), id);
        copyFields(request, category);
        updateById(category);
        return category;
    }

    @Override
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("类别不存在");
        }
        removeById(id);
    }

    private void copyFields(ExpenseCategoryRequest request, ExpenseCategory category) {
        category.setName(request.getName());
        category.setSort(request.getSort() == null ? 0 : request.getSort());
        category.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    /** 类别名称唯一（排除自身） */
    private void requireUniqueName(String name, Long excludeId) {
        long count = lambdaQuery()
                .eq(ExpenseCategory::getName, name)
                .ne(excludeId != null, ExpenseCategory::getId, excludeId)
                .count();
        if (count > 0) {
            throw new BusinessException("类别名称已存在");
        }
    }
}
