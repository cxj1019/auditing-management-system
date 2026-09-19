package com.accounting.firm.confirmation.service;

import com.accounting.firm.common.security.SecurityUser;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 函证智能批量导入：扫描 PDF → 按页识别被函证单位 → 分组 → 人工确认 → 拆分归档到项目
 */
public interface ConfirmationIntakeService {

    /**
     * 解析扫描 PDF：按页渲染识别被函证单位并分组，返回
     * [{pages:[1,2], unit:"XX银行", projectId, projectName}]（projectId 为 AI 建议，允许 null）
     */
    List<Map<String, Object>> parseIntake(MultipartFile pdf) throws Exception;

    /**
     * 确认归档：groups = [{pages:[1,2], unit:"XX银行", projectId:8}]
     * 按分组拆分 PDF 存为各函证的"原始函证"附件；目标项目下无同名被函证单位时自动创建函证记录。
     * 返回 {created, attached, skipped}
     */
    Map<String, Object> confirmIntake(MultipartFile pdf, List<Map<String, Object>> groups, SecurityUser currentUser) throws Exception;
}
