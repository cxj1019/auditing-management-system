package com.accounting.firm.reimbursement.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.reimbursement.dto.ApproveRequest;
import com.accounting.firm.reimbursement.dto.FinanceRequest;
import com.accounting.firm.reimbursement.dto.ReimbursementExportVO;
import com.accounting.firm.reimbursement.dto.ReimbursementRequest;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.accounting.firm.reimbursement.service.ReimbursementAttachmentService;
import com.accounting.firm.reimbursement.service.ReimbursementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.time.LocalDate;
import java.util.List;

/**
 * 报销管理接口（单头 + 明细行 + 生命周期 + 二级审批 + 财务环节）
 */
@RestController
@RequestMapping("/api/reimbursements")
@RequiredArgsConstructor
public class ReimbursementController {

    private final ReimbursementService reimbursementService;
    private final ReimbursementAttachmentService reimbursementAttachmentService;
    private final com.accounting.firm.common.storage.SupabaseStorageService storageService;

    /** 分页筛选查询报销单 */
    @PreAuthorize("hasAuthority('business:reimbursement:list')")
    @GetMapping
    public ApiResult<PageResult<Reimbursement>> page(@RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(required = false) String keyword) {
        return ApiResult.success(reimbursementService.pageReimbursements(current, size, status, keyword));
    }

    /** 报销单明细行清单 */
    @PreAuthorize("hasAuthority('business:reimbursement:list')")
    @GetMapping("/{id}/items")
    public ApiResult<List<com.accounting.firm.reimbursement.entity.ReimbursementItem>> items(@PathVariable Long id) {
        return ApiResult.success(reimbursementService.listItems(id));
    }

    /** 创建报销单草稿（含明细行），返回草稿 ID（供明细行上传发票附件） */
    @AuditLog("创建报销草稿")
    @PreAuthorize("hasAuthority('business:reimbursement:add')")
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody ReimbursementRequest request,
                                  @AuthenticationPrincipal SecurityUser currentUser) {
        return ApiResult.success(reimbursementService.createDraft(request, currentUser));
    }

    /** 更新草稿（替换明细行） */
    @AuditLog("编辑报销草稿")
    @PreAuthorize("hasAuthority('business:reimbursement:edit')")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id,
                                  @Valid @RequestBody ReimbursementRequest request,
                                  @AuthenticationPrincipal SecurityUser currentUser) {
        reimbursementService.updateDraft(id, request, currentUser);
        return ApiResult.success();
    }

    /** 提交草稿 */
    @AuditLog("提交报销单")
    @PreAuthorize("hasAuthority('business:reimbursement:edit')")
    @PutMapping("/{id}/submit")
    public ApiResult<Void> submit(@PathVariable Long id,
                                  @AuthenticationPrincipal SecurityUser currentUser) {
        reimbursementService.submitDraft(id, currentUser);
        return ApiResult.success();
    }

    /** 撤回待审批单据 */
    @AuditLog("撤回报销单")
    @PreAuthorize("hasAuthority('business:reimbursement:edit')")
    @PutMapping("/{id}/withdraw")
    public ApiResult<Void> withdraw(@PathVariable Long id,
                                    @AuthenticationPrincipal SecurityUser currentUser) {
        reimbursementService.withdraw(id, currentUser);
        return ApiResult.success();
    }

    /** 删除草稿 */
    @AuditLog("删除报销单")
    @PreAuthorize("hasAuthority('business:reimbursement:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id,
                                  @AuthenticationPrincipal SecurityUser currentUser) {
        reimbursementService.deleteDraft(id, currentUser);
        return ApiResult.success();
    }

    /** 审批（一级批准/驳回/转终审；终审仅 admin） */
    @AuditLog("审批报销单")
    @PreAuthorize("hasAuthority('business:reimbursement:approve')")
    @PutMapping("/{id}/approve")
    public ApiResult<Void> approve(@PathVariable Long id,
                                   @Valid @RequestBody ApproveRequest request,
                                   @AuthenticationPrincipal SecurityUser currentUser) {
        reimbursementService.approve(id, request, currentUser);
        return ApiResult.success();
    }

    /** 财务操作：receive-invoice / mark-paid */
    @AuditLog("报销财务操作")
    @PreAuthorize("hasAuthority('business:reimbursement:finance')")
    @PutMapping("/{id}/finance")
    public ApiResult<Void> finance(@PathVariable Long id,
                                   @Valid @RequestBody FinanceRequest request) {
        reimbursementService.finance(id, request);
        return ApiResult.success();
    }

    /** 导出费用明细扁平行（按明细费用日期范围） */
    @PreAuthorize("hasAuthority('business:reimbursement:list')")
    @GetMapping("/export-items")
    public ApiResult<List<ReimbursementExportVO>> exportItems(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResult.success(reimbursementService.exportItems(startDate, endDate));
    }

    /** 附件清单 */
    @PreAuthorize("hasAuthority('business:reimbursement:list')")
    @GetMapping("/{id}/attachments")
    public ApiResult<List<com.accounting.firm.reimbursement.entity.ReimbursementAttachment>> attachments(
            @PathVariable Long id) {
        return ApiResult.success(reimbursementAttachmentService.listByReimbursementId(id));
    }

    /** 上传发票附件（仅本人草稿态；itemId 可选关联明细行） */
    @AuditLog("上传报销附件")
    @PreAuthorize("hasAuthority('business:reimbursement:edit')")
    @PostMapping("/{id}/attachments")
    public ApiResult<com.accounting.firm.reimbursement.entity.ReimbursementAttachment> uploadAttachment(
            @PathVariable Long id,
            @RequestParam(required = false) Long itemId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal SecurityUser currentUser) {
        return ApiResult.success(reimbursementAttachmentService.upload(id, itemId, file, currentUser));
    }

    /** 下载附件（以原始文件名返回） */
    @PreAuthorize("hasAuthority('business:reimbursement:list')")
    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long id,
                                                     @PathVariable Long attachmentId) {
        com.accounting.firm.reimbursement.entity.ReimbursementAttachment attachment =
                reimbursementAttachmentService.getById(attachmentId);
        if (attachment == null || !attachment.getReimbursementId().equals(id)) {
            return ResponseEntity.notFound().build();
        }
        byte[] content = reimbursementAttachmentService.downloadFile(attachment);
        String encodedName = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .body(content);
    }

    /** 删除附件（仅本人草稿态） */
    @AuditLog("删除报销附件")
    @PreAuthorize("hasAuthority('business:reimbursement:edit')")
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ApiResult<Void> deleteAttachment(@PathVariable Long id,
                                            @PathVariable Long attachmentId,
                                            @AuthenticationPrincipal SecurityUser currentUser) {
        reimbursementAttachmentService.deleteAttachment(attachmentId, currentUser);
        return ApiResult.success();
    }

    /** 获取预览签名 URL */
    @PreAuthorize("hasAuthority('business:reimbursement:list')")
    @GetMapping("/{id}/attachments/{attachmentId}/preview-url")
    public ApiResult<String> previewUrl(@PathVariable Long id, @PathVariable Long attachmentId) {
        var att = reimbursementAttachmentService.getById(attachmentId);
        if (att == null || !att.getReimbursementId().equals(id)) {
            return ApiResult.error(com.accounting.firm.common.api.ResultCode.NOT_FOUND);
        }
        return ApiResult.success(storageService.createSignedUrl(att.getStoredName()));
    }
}
