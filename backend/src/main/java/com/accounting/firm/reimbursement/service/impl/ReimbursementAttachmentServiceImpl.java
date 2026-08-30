package com.accounting.firm.reimbursement.service.impl;

import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.common.storage.SupabaseStorageService;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.accounting.firm.reimbursement.entity.ReimbursementAttachment;
import com.accounting.firm.reimbursement.entity.ReimbursementStatus;
import com.accounting.firm.reimbursement.mapper.ReimbursementAttachmentMapper;
import com.accounting.firm.reimbursement.mapper.ReimbursementMapper;
import com.accounting.firm.reimbursement.service.ReimbursementAttachmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 报销附件服务实现：存储于 Supabase Storage 私有桶
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReimbursementAttachmentServiceImpl implements ReimbursementAttachmentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final ReimbursementAttachmentMapper attachmentMapper;
    private final ReimbursementMapper reimbursementMapper;
    private final com.accounting.firm.reimbursement.mapper.ReimbursementItemMapper itemMapper;
    private final SupabaseStorageService storageService;

    @Override
    public ReimbursementAttachment upload(Long reimbursementId, Long itemId, MultipartFile file, SecurityUser currentUser) {
        Reimbursement bill = requireDraftOwner(reimbursementId, currentUser);
        // itemId 归属校验
        if (itemId != null) {
            com.accounting.firm.reimbursement.entity.ReimbursementItem item =
                    itemMapper.selectById(itemId);
            if (item == null || !item.getReimbursementId().equals(reimbursementId)) {
                throw new BusinessException("明细行不存在或不属于该报销单");
            }
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过 20MB");
        }
        String original = file.getOriginalFilename();
        String extension = extractExtension(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("发票附件仅支持 PDF 与图片（jpg/png）格式");
        }
        String storedName = "reimbursements/" + reimbursementId + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            storageService.upload(storedName, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            throw new BusinessException("读取上传文件失败，请重试");
        }

        ReimbursementAttachment attachment = new ReimbursementAttachment();
        attachment.setReimbursementId(reimbursementId);
        attachment.setItemId(itemId);
        attachment.setFileName(original);
        attachment.setStoredName(storedName);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(file.getContentType());
        attachmentMapper.insert(attachment);
        return attachment;
    }

    @Override
    public List<ReimbursementAttachment> listByReimbursementId(Long reimbursementId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<ReimbursementAttachment>()
                .eq(ReimbursementAttachment::getReimbursementId, reimbursementId)
                .orderByDesc(ReimbursementAttachment::getCreateTime));
    }

    @Override
    public ReimbursementAttachment getById(Long attachmentId) {
        return attachmentMapper.selectById(attachmentId);
    }

    @Override
    public byte[] downloadFile(ReimbursementAttachment attachment) {
        return storageService.download(attachment.getStoredName());
    }

    @Override
    public void deleteAttachment(Long attachmentId, SecurityUser currentUser) {
        ReimbursementAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        requireDraftOwner(attachment.getReimbursementId(), currentUser);
        attachmentMapper.deleteById(attachmentId);
        try {
            storageService.delete(attachment.getStoredName());
        } catch (Exception e) {
            log.warn("报销附件远端对象清理失败: {}", attachment.getStoredName(), e);
        }
    }

    /** 校验单据存在、为草稿态且操作者为申请人 */
    private Reimbursement requireDraftOwner(Long reimbursementId, SecurityUser currentUser) {
        Reimbursement bill = reimbursementMapper.selectById(reimbursementId);
        if (bill == null) {
            throw new BusinessException("报销单不存在");
        }
        if (bill.getStatus() != ReimbursementStatus.DRAFT.getCode()) {
            throw new BusinessException("仅草稿状态的报销单可管理附件");
        }
        if (!currentUser.getUsername().equals(bill.getApplicantUsername())) {
            throw new BusinessException("仅申请人可以管理草稿附件");
        }
        return bill;
    }

    /** 提取小写扩展名 */
    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
