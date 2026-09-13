package com.accounting.firm.vendor.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.vendor.entity.VendorPayment;
import com.accounting.firm.vendor.entity.VendorPaymentAttachment;
import com.accounting.firm.vendor.service.VendorPaymentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 对公付款：登记 → 审批 → 付款，可归集项目计入成本
 */
@RestController
@RequestMapping("/api/vendor-payments")
@RequiredArgsConstructor
public class VendorPaymentController {

    private final VendorPaymentService vendorPaymentService;
    private final com.accounting.firm.common.storage.SupabaseStorageService storageService;

    /** 分页筛选查询（员工仅本人登记的） */
    @PreAuthorize("hasAuthority('business:vendor:list')")
    @GetMapping
    public ApiResult<PageResult<VendorPayment>> page(@AuthenticationPrincipal SecurityUser currentUser,
                                                     @RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(required = false) String keyword) {
        return ApiResult.success(vendorPaymentService.page(current, size, status, keyword, currentUser));
    }

    /** 登记付款单（草稿） */
    @PreAuthorize("hasAuthority('business:vendor:add')")
    @PostMapping
    public ApiResult<Long> create(@RequestBody VendorPayment request,
                                  @AuthenticationPrincipal SecurityUser currentUser) {
        return ApiResult.success(vendorPaymentService.create(request, currentUser));
    }

    /** 编辑（仅草稿/驳回，本人） */
    @PreAuthorize("hasAuthority('business:vendor:edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody VendorPayment request,
                                  @AuthenticationPrincipal SecurityUser currentUser) {
        vendorPaymentService.update(request, currentUser);
        return ApiResult.success();
    }

    /** 提交审批（本人） */
    @PreAuthorize("hasAuthority('business:vendor:edit')")
    @PutMapping("/{id}/submit")
    public ApiResult<Void> submit(@PathVariable Long id, @AuthenticationPrincipal SecurityUser currentUser) {
        vendorPaymentService.submit(id, currentUser);
        return ApiResult.success();
    }

    /** 撤回（本人，待审批回草稿） */
    @PreAuthorize("hasAuthority('business:vendor:edit')")
    @PutMapping("/{id}/withdraw")
    public ApiResult<Void> withdraw(@PathVariable Long id, @AuthenticationPrincipal SecurityUser currentUser) {
        vendorPaymentService.withdraw(id, currentUser);
        return ApiResult.success();
    }

    /** 删除（仅草稿/驳回，本人） */
    @PreAuthorize("hasAuthority('business:vendor:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, @AuthenticationPrincipal SecurityUser currentUser) {
        vendorPaymentService.delete(id, currentUser);
        return ApiResult.success();
    }

    /** 审批：approve 批准 / reject 驳回 */
    @PreAuthorize("hasAuthority('business:vendor:approve')")
    @PutMapping("/{id}/approve")
    public ApiResult<Void> approve(@PathVariable Long id,
                                   @RequestParam String action,
                                   @RequestParam(required = false) String comment,
                                   @AuthenticationPrincipal SecurityUser currentUser) {
        vendorPaymentService.approve(id, action, comment, currentUser);
        return ApiResult.success();
    }

    /** 标记已付款 */
    @PreAuthorize("hasAuthority('business:vendor:approve')")
    @PutMapping("/{id}/mark-paid")
    public ApiResult<Void> markPaid(@PathVariable Long id, @AuthenticationPrincipal SecurityUser currentUser) {
        vendorPaymentService.markPaid(id, currentUser);
        return ApiResult.success();
    }

    // ---------- 附件（供应商发票扫描件/付款凭证） ----------

    @PreAuthorize("hasAuthority('business:vendor:list')")
    @GetMapping("/{id}/attachments")
    public ApiResult<List<VendorPaymentAttachment>> attachments(@PathVariable Long id) {
        return ApiResult.success(vendorPaymentService.listAttachments(id));
    }

    @PreAuthorize("hasAuthority('business:vendor:edit')")
    @PostMapping("/{id}/attachments")
    public ApiResult<VendorPaymentAttachment> uploadAttachment(@PathVariable Long id,
                                                               @RequestParam("file") MultipartFile file,
                                                               @AuthenticationPrincipal SecurityUser currentUser) {
        return ApiResult.success(vendorPaymentService.uploadAttachment(id, file, currentUser));
    }

    @PreAuthorize("hasAuthority('business:vendor:edit')")
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ApiResult<Void> deleteAttachment(@PathVariable Long id,
                                            @PathVariable Long attachmentId,
                                            @AuthenticationPrincipal SecurityUser currentUser) {
        vendorPaymentService.deleteAttachment(id, attachmentId, currentUser);
        return ApiResult.success();
    }

    @PreAuthorize("hasAuthority('business:vendor:list')")
    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public org.springframework.http.ResponseEntity<byte[]> downloadAttachment(@PathVariable Long id,
                                                                              @PathVariable Long attachmentId) {
        VendorPaymentAttachment attachment = vendorPaymentService.listAttachments(id).stream()
                .filter(a -> a.getId().equals(attachmentId))
                .findFirst().orElse(null);
        if (attachment == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        byte[] content = vendorPaymentService.downloadAttachment(attachmentId);
        String encoded = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(content);
    }
}
