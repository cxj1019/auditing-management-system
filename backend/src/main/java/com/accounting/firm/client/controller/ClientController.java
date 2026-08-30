package com.accounting.firm.client.controller;

import com.accounting.firm.client.dto.ClientRequest;
import com.accounting.firm.client.entity.Client;
import com.accounting.firm.client.service.ClientService;
import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.PageResult;
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

/**
 * 客户管理接口
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @PreAuthorize("hasAuthority('business:client:list')")
    @GetMapping
    public ApiResult<PageResult<Client>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String clientType,
                                              @RequestParam(required = false) Long deptId) {
        return ApiResult.success(clientService.pageClients(current, size, keyword, clientType, deptId));
    }

    @AuditLog("登记客户")
    @PreAuthorize("hasAuthority('business:client:add')")
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody ClientRequest request) {
        return ApiResult.success(clientService.createClient(request));
    }

    @AuditLog("编辑客户")
    @PreAuthorize("hasAuthority('business:client:edit')")
    @PutMapping
    public ApiResult<Void> update(@Valid @RequestBody ClientRequest request) {
        clientService.updateClient(request);
        return ApiResult.success();
    }

    @AuditLog("删除客户")
    @PreAuthorize("hasAuthority('business:client:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ApiResult.success();
    }
}
