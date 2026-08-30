package com.accounting.firm.confirmation.service;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.confirmation.dto.ConfirmationRequest;
import com.accounting.firm.confirmation.entity.Confirmation;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;

/**
 * 函证服务
 */
public interface ConfirmationService extends IService<Confirmation> {

    /** 分页筛选查询函证（含逾期标记计算，可按项目筛选） */
    PageResult<Confirmation> pageConfirmations(long current, long size,
                                               Integer status, String type, String keyword,
                                               Long projectId);

    /** 登记函证（自动编号，初始状态未发出） */
    void createConfirmation(ConfirmationRequest request);

    /** 编辑函证基本信息（编号与状态不可修改） */
    void updateConfirmation(ConfirmationRequest request);

    /** 删除函证（仅未发出可删） */
    void deleteConfirmation(Long id);

    /**
     * 状态流转
     *
     * @param id     函证 ID
     * @param action 动作：send-发出 confirm-回函 void-作废
     * @param date   发出/回函日期（void 时可为空）
     */
    void changeStatus(Long id, String action, LocalDate date);
}
