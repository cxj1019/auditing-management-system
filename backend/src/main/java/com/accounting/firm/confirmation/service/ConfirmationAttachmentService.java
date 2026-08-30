package com.accounting.firm.confirmation.service;

import com.accounting.firm.confirmation.entity.ConfirmationAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ConfirmationAttachmentService {

    /** 上传附件（attachmentType: original/reply） */
    ConfirmationAttachment upload(Long confirmationId, String attachmentType, MultipartFile file);

    /** 附件清单（可按类别筛选） */
    List<ConfirmationAttachment> listByConfirmationId(Long confirmationId, String attachmentType);

    /** 按 ID 查询 */
    ConfirmationAttachment getById(Long id);

    /** 下载附件内容 */
    byte[] downloadFile(ConfirmationAttachment attachment);

    /** 删除附件 */
    void deleteAttachment(Long attachmentId);

    /** 保存附件记录（不做上传，用于外部生成的文件如物流截图） */
    ConfirmationAttachment uploadAttachment(ConfirmationAttachment attachment);
}
