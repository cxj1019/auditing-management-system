package com.accounting.firm.contract.service.impl;

import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.storage.SupabaseStorageService;
import com.accounting.firm.contract.entity.Contract;
import com.accounting.firm.contract.entity.ContractAttachment;
import com.accounting.firm.contract.mapper.ContractAttachmentMapper;
import com.accounting.firm.contract.mapper.ContractMapper;
import com.accounting.firm.contract.service.ContractAttachmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 合同附件服务实现：文件存储于 Supabase Storage（私有桶）
 */
@Slf4j
@Service
public class ContractAttachmentServiceImpl extends ServiceImpl<ContractAttachmentMapper, ContractAttachment>
        implements ContractAttachmentService {

    /** 允许的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "doc", "docx");

    /** 单文件大小上限（字节）：20MB */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final ContractMapper contractMapper;
    private final SupabaseStorageService storageService;

    public ContractAttachmentServiceImpl(ContractMapper contractMapper,
                                         SupabaseStorageService storageService) {
        this.contractMapper = contractMapper;
        this.storageService = storageService;
    }

    @Override
    public ContractAttachment upload(Long contractId, MultipartFile file) {
        if (contractMapper.selectById(contractId) == null) {
            throw new BusinessException("合同不存在");
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
            throw new BusinessException("仅支持 PDF、图片（jpg/png）与 Word 文档格式");
        }

        // 对象路径：contracts/{contractId}/{uuid}.{ext}
        String storedName = "contracts/" + contractId + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            throw new BusinessException("读取上传文件失败，请重试");
        }
        storageService.upload(storedName, data, file.getContentType());

        ContractAttachment attachment = new ContractAttachment();
        attachment.setContractId(contractId);
        attachment.setFileName(original);
        attachment.setStoredName(storedName);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(file.getContentType());
        save(attachment);
        return attachment;
    }

    @Override
    public List<ContractAttachment> listByContractId(Long contractId) {
        return list(new LambdaQueryWrapper<ContractAttachment>()
                .eq(ContractAttachment::getContractId, contractId)
                .orderByDesc(ContractAttachment::getCreateTime));
    }

    @Override
    public byte[] downloadFile(ContractAttachment attachment) {
        return storageService.download(attachment.getStoredName());
    }

    @Override
    public void deleteAttachment(Long attachmentId) {
        ContractAttachment attachment = getById(attachmentId);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        removeById(attachmentId);
        // 远端对象清理（失败仅记日志，不阻断记录删除）
        try {
            storageService.delete(attachment.getStoredName());
        } catch (Exception e) {
            log.warn("附件远端对象清理失败: {}", attachment.getStoredName(), e);
        }
    }

    /** 提取小写扩展名 */
    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
