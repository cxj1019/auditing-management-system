package com.accounting.firm.confirmation.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.confirmation.dto.ConfirmationRequest;
import com.accounting.firm.confirmation.entity.Confirmation;
import com.accounting.firm.confirmation.entity.ConfirmationAttachment;
import com.accounting.firm.confirmation.service.ConfirmationAttachmentService;
import com.accounting.firm.confirmation.service.ConfirmationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 函证管理接口
 */
@RestController
@RequestMapping("/api/confirmations")
@RequiredArgsConstructor
public class ConfirmationController {

    private final ConfirmationService confirmationService;
    private final ConfirmationAttachmentService attachmentService;
    private final com.accounting.firm.common.storage.SupabaseStorageService storageService;
    private final com.accounting.firm.confirmation.service.LogisticsScreenshotService logisticsScreenshotService;

    /** 分页筛选查询函证 */
    @PreAuthorize("hasAuthority('business:confirmation:list')")
    @GetMapping
    public ApiResult<PageResult<Confirmation>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long projectId) {
        return ApiResult.success(confirmationService.pageConfirmations(current, size, status, type, keyword, projectId));
    }

    /** 登记函证 */
    @AuditLog("登记函证")
    @PreAuthorize("hasAuthority('business:confirmation:add')")
    @PostMapping
    public ApiResult<Void> create(@Valid @RequestBody ConfirmationRequest request) {
        confirmationService.createConfirmation(request);
        return ApiResult.success();
    }

    /** 编辑函证基本信息 */
    @AuditLog("编辑函证")
    @PreAuthorize("hasAuthority('business:confirmation:edit')")
    @PutMapping
    public ApiResult<Void> update(@Valid @RequestBody ConfirmationRequest request) {
        confirmationService.updateConfirmation(request);
        return ApiResult.success();
    }

    /** 删除函证（仅未发出） */
    @AuditLog("删除函证")
    @PreAuthorize("hasAuthority('business:confirmation:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        confirmationService.deleteConfirmation(id);
        return ApiResult.success();
    }

    /** 状态流转：action=send|confirm|void */
    @AuditLog("函证状态流转")
    @PreAuthorize("hasAuthority('business:confirmation:status')")
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Long id,
                                        @RequestParam String action,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        confirmationService.changeStatus(id, action, date);
        return ApiResult.success();
    }

    // ---------- 附件 ----------

    /** 附件清单（可按类别筛选） */
    @PreAuthorize("hasAuthority('business:confirmation:list')")
    @GetMapping("/{id}/attachments")
    public ApiResult<List<ConfirmationAttachment>> listAttachments(@PathVariable Long id,
                                                                     @RequestParam(required = false) String attachmentType) {
        return ApiResult.success(attachmentService.listByConfirmationId(id, attachmentType));
    }

    /** 上传附件（attachmentType=original|reply） */
    @AuditLog("上传函证附件")
    @PreAuthorize("hasAuthority('business:confirmation:edit')")
    @PostMapping("/{id}/attachments")
    public ApiResult<ConfirmationAttachment> uploadAttachment(@PathVariable Long id,
                                                               @RequestParam String attachmentType,
                                                               @RequestParam("file") MultipartFile file) {
        return ApiResult.success(attachmentService.upload(id, attachmentType, file));
    }

    /** 下载附件 */
    @PreAuthorize("hasAuthority('business:confirmation:list')")
    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long id,
                                                      @PathVariable Long attachmentId) {
        ConfirmationAttachment att = attachmentService.getById(attachmentId);
        if (att == null || !att.getConfirmationId().equals(id)) {
            return ResponseEntity.notFound().build();
        }
        byte[] content = attachmentService.downloadFile(att);
        String encodedName = URLEncoder.encode(att.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(content);
    }

    /** 删除附件 */
    @AuditLog("删除函证附件")
    @PreAuthorize("hasAuthority('business:confirmation:edit')")
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ApiResult<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        attachmentService.deleteAttachment(attachmentId);
        return ApiResult.success();
    }

    /** 获取预览签名 URL */
    @PreAuthorize("hasAuthority('business:confirmation:list')")
    @GetMapping("/{id}/attachments/{attachmentId}/preview-url")
    public ApiResult<String> previewUrl(@PathVariable Long id, @PathVariable Long attachmentId) {
        ConfirmationAttachment att = attachmentService.getById(attachmentId);
        if (att == null || !att.getConfirmationId().equals(id)) {
            return ApiResult.error(com.accounting.firm.common.api.ResultCode.NOT_FOUND);
        }
        return ApiResult.success(storageService.createSignedUrl(att.getStoredName()));
    }

    /** 查询物流并截图（action=send 发出物流 / reply 回函物流） */
    @AuditLog("查询函证物流")
    @PreAuthorize("hasAuthority('business:confirmation:list')")
    @PostMapping("/{id}/track-logistics")
    public ApiResult<ConfirmationAttachment> trackLogistics(@PathVariable Long id,
                                                             @RequestParam String action) {
        Confirmation confirmation = confirmationService.getById(id);
        if (confirmation == null) {
            throw new com.accounting.firm.common.exception.BusinessException("函证不存在");
        }
        String trackingNo = "send".equals(action)
                ? confirmation.getSendTrackingNo()
                : confirmation.getReplyTrackingNo();
        if (!org.springframework.util.StringUtils.hasText(trackingNo)) {
            throw new com.accounting.firm.common.exception.BusinessException(
                    "send".equals(action) ? "请先填写发出快递单号" : "请先填写回函快递单号");
        }
        byte[] screenshot = logisticsScreenshotService.screenshotLogistics(trackingNo);
        String storedName = "confirmations/" + id + "/logistics_"
                + action + "_" + System.currentTimeMillis() + ".png";
        storageService.upload(storedName, screenshot, "image/png");

        ConfirmationAttachment att = new ConfirmationAttachment();
        att.setConfirmationId(id);
        att.setAttachmentType(action + "_logistics");
        att.setFileName("物流跟踪_" + trackingNo + ".png");
        att.setStoredName(storedName);
        att.setFileSize((long) screenshot.length);
        att.setContentType("image/png");
        attachmentService.uploadAttachment(att);
        return ApiResult.success(att);
    }
}
