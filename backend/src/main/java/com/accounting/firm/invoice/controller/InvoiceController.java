package com.accounting.firm.invoice.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.storage.SupabaseStorageService;
import com.accounting.firm.invoice.dto.InvoiceOptionVO;
import com.accounting.firm.invoice.dto.InvoiceRequest;
import com.accounting.firm.invoice.dto.InvoiceSummaryVO;
import com.accounting.firm.invoice.dto.InvoiceVO;
import com.accounting.firm.invoice.entity.InvoiceAttachment;
import com.accounting.firm.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 发票管理接口
 */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final SupabaseStorageService storageService;

    /** 分页筛选查询发票 */
    @PreAuthorize("hasAuthority('business:invoice:list')")
    @GetMapping
    public ApiResult<PageResult<InvoiceVO>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(required = false) Integer status) {
        return ApiResult.success(invoiceService.pageInvoices(current, size, keyword, type, status));
    }

    /** 已开票发票下拉选项（供收款核销选择） */
    @PreAuthorize("hasAnyAuthority('business:invoice:list', 'business:collection:list')")
    @GetMapping("/options")
    public ApiResult<List<InvoiceOptionVO>> options(@RequestParam(required = false) String keyword) {
        return ApiResult.success(invoiceService.options(keyword));
    }

    /** 登记发票 */
    @AuditLog("登记发票")
    @PreAuthorize("hasAuthority('business:invoice:add')")
    @PostMapping
    public ApiResult<Void> create(@Valid @RequestBody InvoiceRequest request) {
        invoiceService.createInvoice(request);
        return ApiResult.success();
    }

    /** 编辑发票 */
    @AuditLog("编辑发票")
    @PreAuthorize("hasAuthority('business:invoice:edit')")
    @PutMapping
    public ApiResult<Void> update(@Valid @RequestBody InvoiceRequest request) {
        invoiceService.updateInvoice(request);
        return ApiResult.success();
    }

    /** 删除发票 */
    @AuditLog("删除发票")
    @PreAuthorize("hasAuthority('business:invoice:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        invoiceService.deleteInvoice(id);
        return ApiResult.success();
    }

    /** 状态流转（action=issue 开票 / void 作废） */
    @AuditLog("发票状态流转")
    @PreAuthorize("hasAuthority('business:invoice:status')")
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Long id,
                                        @RequestParam String action,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDate) {
        invoiceService.changeStatus(id, action, invoiceDate);
        return ApiResult.success();
    }

    // ---------- 附件（发票扫描件） ----------

    /** 附件清单 */
    @PreAuthorize("hasAuthority('business:invoice:list')")
    @GetMapping("/{id}/attachments")
    public ApiResult<List<InvoiceAttachment>> listAttachments(@PathVariable Long id) {
        return ApiResult.success(invoiceService.listAttachments(id));
    }

    /** 上传发票扫描件 */
    @AuditLog("上传发票扫描件")
    @PreAuthorize("hasAuthority('business:invoice:edit')")
    @PostMapping("/{id}/attachments")
    public ApiResult<InvoiceAttachment> uploadAttachment(@PathVariable Long id,
                                                         @RequestParam("file") MultipartFile file) {
        return ApiResult.success(invoiceService.uploadAttachment(id, file));
    }

    /** 下载附件 */
    @PreAuthorize("hasAuthority('business:invoice:list')")
    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long id,
                                                     @PathVariable Long attachmentId) {
        InvoiceAttachment att = invoiceService.getAttachment(id, attachmentId);
        byte[] content = invoiceService.downloadAttachment(att);
        String encodedName = URLEncoder.encode(att.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName)
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        att.getContentType() == null ? "application/octet-stream" : att.getContentType()))
                .body(content);
    }

    /** 获取预览签名 URL */
    @PreAuthorize("hasAuthority('business:invoice:list')")
    @GetMapping("/{id}/attachments/{attachmentId}/preview-url")
    public ApiResult<String> previewUrl(@PathVariable Long id, @PathVariable Long attachmentId) {
        InvoiceAttachment att = invoiceService.getAttachment(id, attachmentId);
        return ApiResult.success(storageService.createSignedUrl(att.getStoredName()));
    }

    /** 删除附件 */
    @AuditLog("删除发票附件")
    @PreAuthorize("hasAuthority('business:invoice:edit')")
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ApiResult<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        invoiceService.deleteAttachment(id, attachmentId);
        return ApiResult.success();
    }
}
