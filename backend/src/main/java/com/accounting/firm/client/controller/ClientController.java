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
    private final com.accounting.firm.client.service.ClientContactService clientContactService;

    /** 客户联系人清单 */
    @PreAuthorize("hasAnyAuthority('business:client:list', 'business:client:edit', 'business:client:add')")
    @GetMapping("/{clientId}/contacts")
    public ApiResult<java.util.List<com.accounting.firm.client.entity.ClientContact>> contacts(
            @PathVariable Long clientId) {
        return ApiResult.success(clientContactService.listByClientId(clientId));
    }

    /** 新增联系人 */
    @AuditLog("新增客户联系人")
    @PreAuthorize("hasAnyAuthority('business:client:edit', 'business:client:add')")
    @PostMapping("/{clientId}/contacts")
    public ApiResult<com.accounting.firm.client.entity.ClientContact> addContact(
            @PathVariable Long clientId,
            @Valid @RequestBody com.accounting.firm.client.dto.ClientContactRequest request) {
        return ApiResult.success(clientContactService.addContact(clientId, request));
    }

    /** 编辑联系人 */
    @AuditLog("编辑客户联系人")
    @PreAuthorize("hasAnyAuthority('business:client:edit', 'business:client:add')")
    @PutMapping("/{clientId}/contacts/{contactId}")
    public ApiResult<com.accounting.firm.client.entity.ClientContact> updateContact(
            @PathVariable Long clientId,
            @PathVariable Long contactId,
            @Valid @RequestBody com.accounting.firm.client.dto.ClientContactRequest request) {
        return ApiResult.success(clientContactService.updateContact(contactId, request));
    }

    /** 删除联系人 */
    @AuditLog("删除客户联系人")
    @PreAuthorize("hasAnyAuthority('business:client:edit', 'business:client:add', 'business:client:delete')")
    @DeleteMapping("/{clientId}/contacts/{contactId}")
    public ApiResult<Void> deleteContact(@PathVariable Long clientId, @PathVariable Long contactId) {
        clientContactService.deleteContact(clientId, contactId);
        return ApiResult.success();
    }

    @PreAuthorize("hasAuthority('business:client:list')")
    @GetMapping
    public ApiResult<PageResult<Client>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String clientType) {
        return ApiResult.success(clientService.pageClients(current, size, keyword, clientType));
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
