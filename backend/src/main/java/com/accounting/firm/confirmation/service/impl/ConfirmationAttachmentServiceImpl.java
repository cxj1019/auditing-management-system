package com.accounting.firm.confirmation.service.impl;

import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.storage.SupabaseStorageService;
import com.accounting.firm.confirmation.entity.Confirmation;
import com.accounting.firm.confirmation.entity.ConfirmationAttachment;
import com.accounting.firm.confirmation.mapper.ConfirmationAttachmentMapper;
import com.accounting.firm.confirmation.mapper.ConfirmationMapper;
import com.accounting.firm.confirmation.service.ConfirmationAttachmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmationAttachmentServiceImpl implements ConfirmationAttachmentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final ConfirmationAttachmentMapper attachmentMapper;
    private final ConfirmationMapper confirmationMapper;
    private final SupabaseStorageService storageService;

    @Override
    public ConfirmationAttachment upload(Long confirmationId, String attachmentType, MultipartFile file) {
        if (confirmationMapper.selectById(confirmationId) == null) {
            throw new BusinessException("函证不存在");
        }
        if (!"original".equals(attachmentType) && !"reply".equals(attachmentType)) {
            throw new BusinessException("附件类别必须为 original 或 reply");
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
            throw new BusinessException("仅支持 PDF 与图片（jpg/png）格式");
        }
        String storedName = "confirmations/" + confirmationId + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            storageService.upload(storedName, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败，请重试");
        }

        ConfirmationAttachment att = new ConfirmationAttachment();
        att.setConfirmationId(confirmationId);
        att.setAttachmentType(attachmentType);
        att.setFileName(original);
        att.setStoredName(storedName);
        att.setFileSize(file.getSize());
        att.setContentType(file.getContentType());
        attachmentMapper.insert(att);
        return att;
    }

    @Override
    public List<ConfirmationAttachment> listByConfirmationId(Long confirmationId, String attachmentType) {
        LambdaQueryWrapper<ConfirmationAttachment> wrapper = new LambdaQueryWrapper<ConfirmationAttachment>()
                .eq(ConfirmationAttachment::getConfirmationId, confirmationId)
                .orderByDesc(ConfirmationAttachment::getCreateTime);
        if (StringUtils.hasText(attachmentType)) {
            wrapper.eq(ConfirmationAttachment::getAttachmentType, attachmentType);
        }
        return attachmentMapper.selectList(wrapper);
    }

    @Override
    public ConfirmationAttachment getById(Long id) {
        return attachmentMapper.selectById(id);
    }

    @Override
    public byte[] downloadFile(ConfirmationAttachment attachment) {
        return storageService.download(attachment.getStoredName());
    }

    @Override
    public void deleteAttachment(Long attachmentId) {
        ConfirmationAttachment att = attachmentMapper.selectById(attachmentId);
        if (att == null) {
            throw new BusinessException("附件不存在");
        }
        attachmentMapper.deleteById(attachmentId);
        try {
            storageService.delete(att.getStoredName());
        } catch (Exception e) {
            log.warn("函证附件远端对象清理失败: {}", att.getStoredName(), e);
        }
    }

    @Override
    public ConfirmationAttachment uploadAttachment(ConfirmationAttachment attachment) {
        attachmentMapper.insert(attachment);
        return attachment;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
