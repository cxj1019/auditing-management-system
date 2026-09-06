package com.accounting.firm.reimbursement.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.reimbursement.dto.ExpenseCategoryRequest;
import com.accounting.firm.reimbursement.entity.ExpenseCategory;
import com.accounting.firm.reimbursement.service.ExpenseCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报销费用类别接口：填报人读取清单，系统管理员增删改
 */
@RestController
@RequestMapping("/api/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    /** 类别清单（报销表单下拉用） */
    @PreAuthorize("hasAnyAuthority('business:reimbursement:list', 'business:reimbursement:add', 'business:reimbursement:category')")
    @GetMapping
    public ApiResult<List<ExpenseCategory>> list() {
        return ApiResult.success(expenseCategoryService.listAll());
    }

    @AuditLog("新增报销类别")
    @PreAuthorize("hasAuthority('business:reimbursement:category')")
    @PostMapping
    public ApiResult<ExpenseCategory> create(@Valid @RequestBody ExpenseCategoryRequest request) {
        return ApiResult.success(expenseCategoryService.create(request));
    }

    @AuditLog("编辑报销类别")
    @PreAuthorize("hasAuthority('business:reimbursement:category')")
    @PutMapping("/{id}")
    public ApiResult<ExpenseCategory> update(@PathVariable Long id,
                                             @Valid @RequestBody ExpenseCategoryRequest request) {
        return ApiResult.success(expenseCategoryService.update(id, request));
    }

    @AuditLog("删除报销类别")
    @PreAuthorize("hasAuthority('business:reimbursement:category')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        expenseCategoryService.delete(id);
        return ApiResult.success();
    }
}
