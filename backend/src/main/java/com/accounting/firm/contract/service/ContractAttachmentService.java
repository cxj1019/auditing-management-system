package com.accounting.firm.contract.service;

import com.accounting.firm.contract.entity.ContractAttachment;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 合同附件服务
 */
public interface ContractAttachmentService extends IService<ContractAttachment> {

    /** 上传附件（校验合同存在、格式白名单与大小限制） */
    ContractAttachment upload(Long contractId, MultipartFile file);

    /** 查询合同附件清单 */
    List<ContractAttachment> listByContractId(Long contractId);

    /** 下载附件内容（从 Supabase Storage 读取） */
    byte[] downloadFile(ContractAttachment attachment);

    /** 删除附件（记录 + 远端对象） */
    void deleteAttachment(Long attachmentId);
}
