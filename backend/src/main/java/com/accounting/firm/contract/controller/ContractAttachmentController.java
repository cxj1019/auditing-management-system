package com.accounting.firm.contract.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.contract.entity.ContractAttachment;
import com.accounting.firm.contract.service.ContractAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 合同附件接口
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/attachments")
@RequiredArgsConstructor
public class ContractAttachmentController {

    private final ContractAttachmentService contractAttachmentService;
    private final com.accounting.firm.common.storage.SupabaseStorageService storageService;

    /** 附件清单 */
    @PreAuthorize("hasAuthority('business:contract:list')")
    @GetMapping
    public ApiResult<List<ContractAttachment>> list(@PathVariable Long contractId) {
        return ApiResult.success(contractAttachmentService.listByContractId(contractId));
    }

    /** 上传附件 */
    @AuditLog("上传合同附件")
    @PreAuthorize("hasAuthority('business:contract:edit')")
    @PostMapping
    public ApiResult<ContractAttachment> upload(@PathVariable Long contractId,
                                                @RequestParam("file") MultipartFile file) {
        return ApiResult.success(contractAttachmentService.upload(contractId, file));
    }

    /** 下载附件（以原始文件名返回） */
    @PreAuthorize("hasAuthority('business:contract:list')")
    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long contractId,
                                           @PathVariable Long attachmentId) {
        ContractAttachment attachment = contractAttachmentService.getById(attachmentId);
        if (attachment == null || !attachment.getContractId().equals(contractId)) {
            return ResponseEntity.notFound().build();
        }
        byte[] content = contractAttachmentService.downloadFile(attachment);
        String encodedName = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .body(content);
    }

    /** 删除附件 */
    @AuditLog("删除合同附件")
    @PreAuthorize("hasAuthority('business:contract:edit')")
    @DeleteMapping("/{attachmentId}")
    public ApiResult<Void> delete(@PathVariable Long contractId,
                                  @PathVariable Long attachmentId) {
        contractAttachmentService.deleteAttachment(attachmentId);
        return ApiResult.success();
    }

    /** 获取预览签名 URL（免鉴权，1 小时有效） */
    @PreAuthorize("hasAuthority('business:contract:list')")
    @GetMapping("/{attachmentId}/preview-url")
    public ApiResult<String> previewUrl(@PathVariable Long contractId, @PathVariable Long attachmentId) {
        ContractAttachment att = contractAttachmentService.getById(attachmentId);
        if (att == null || !att.getContractId().equals(contractId)) {
            return ApiResult.error(com.accounting.firm.common.api.ResultCode.NOT_FOUND);
        }
        return ApiResult.success(storageService.createSignedUrl(att.getStoredName()));
    }
}
