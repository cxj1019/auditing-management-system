package com.accounting.firm.contract.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.contract.dto.ContractRequest;
import com.accounting.firm.contract.dto.ContractVO;
import com.accounting.firm.contract.service.ContractService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合同管理接口
 */
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    /** 非草稿合同下拉选项（供发票登记选择，带出项目/客户/开票信息） */
    @GetMapping("/options")
    public ApiResult<List<com.accounting.firm.contract.dto.ContractOptionVO>> options() {
        return ApiResult.success(contractService.options());
    }

    /** 分页筛选查询合同 */
    @PreAuthorize("hasAuthority('business:contract:list')")
    @GetMapping
    public ApiResult<PageResult<ContractVO>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String name,
                                                @RequestParam(required = false) String clientName,
                                                @RequestParam(required = false) String ownerName,
                                                @RequestParam(required = false) Integer status) {
        return ApiResult.success(
                contractService.pageContracts(current, size, name, clientName, ownerName, status));
    }

    /** 创建合同 */
    @AuditLog("新增合同")
    @PreAuthorize("hasAuthority('business:contract:add')")
    @PostMapping
    public ApiResult<Void> create(@Valid @RequestBody ContractRequest request) {
        contractService.createContract(request);
        return ApiResult.success();
    }

    /** 编辑合同基本信息 */
    @AuditLog("编辑合同")
    @PreAuthorize("hasAuthority('business:contract:edit')")
    @PutMapping
    public ApiResult<Void> update(@Valid @RequestBody ContractRequest request) {
        contractService.updateContract(request);
        return ApiResult.success();
    }

    /** 合同状态流转 */
    @AuditLog("合同状态流转")
    @PreAuthorize("hasAuthority('business:contract:status')")
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        contractService.changeStatus(id, status);
        return ApiResult.success();
    }

    /** 删除合同（仅草稿） */
    @AuditLog("删除合同")
    @PreAuthorize("hasAuthority('business:contract:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        contractService.deleteContract(id);
        return ApiResult.success();
    }
}
