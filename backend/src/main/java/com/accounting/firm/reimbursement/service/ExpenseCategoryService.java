package com.accounting.firm.reimbursement.service;

import com.accounting.firm.reimbursement.dto.ExpenseCategoryRequest;
import com.accounting.firm.reimbursement.entity.ExpenseCategory;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 报销费用类别服务（系统管理员维护）
 */
public interface ExpenseCategoryService extends IService<ExpenseCategory> {

    /** 全量类别清单（按 sort 升序） */
    List<ExpenseCategory> listAll();

    /** 新增类别 */
    ExpenseCategory create(ExpenseCategoryRequest request);

    /** 编辑类别 */
    ExpenseCategory update(Long id, ExpenseCategoryRequest request);

    /** 删除类别（历史单据按名称留存，不受影响） */
    void delete(Long id);
}
