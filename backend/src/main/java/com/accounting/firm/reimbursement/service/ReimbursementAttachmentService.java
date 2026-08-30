package com.accounting.firm.reimbursement.service;

import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.reimbursement.entity.ReimbursementAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 报销附件服务（发票扫描件等，存储于 Supabase Storage）
 */
public interface ReimbursementAttachmentService {

    /** 上传附件（仅本人草稿态；itemId 可选关联到具体明细行） */
    ReimbursementAttachment upload(Long reimbursementId, Long itemId, MultipartFile file, SecurityUser currentUser);

    /** 附件清单 */
    List<ReimbursementAttachment> listByReimbursementId(Long reimbursementId);

    /** 按 ID 查询附件 */
    ReimbursementAttachment getById(Long attachmentId);

    /** 下载附件内容 */
    byte[] downloadFile(ReimbursementAttachment attachment);

    /** 删除附件（记录 + 远端对象；仅本人草稿态） */
    void deleteAttachment(Long attachmentId, SecurityUser currentUser);
}
