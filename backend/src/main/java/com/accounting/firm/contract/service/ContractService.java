package com.accounting.firm.contract.service;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.contract.dto.ContractOptionVO;
import com.accounting.firm.contract.dto.ContractRequest;
import com.accounting.firm.contract.dto.ContractVO;
import com.accounting.firm.contract.entity.Contract;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 合同服务
 */
public interface ContractService extends IService<Contract> {

    /** 非草稿合同下拉选项（带出项目/客户/客户开票信息，供发票登记选择） */
    List<ContractOptionVO> options();

    /** 分页筛选查询合同（带所属项目信息） */
    PageResult<ContractVO> pageContracts(long current, long size, String name,
                                         String clientName, String ownerName, Integer status);

    /** 创建合同（自动生成合同编号，初始状态草稿） */
    void createContract(ContractRequest request);

    /** 编辑合同基本信息（编号与状态不可通过编辑修改） */
    void updateContract(ContractRequest request);

    /** 状态流转（受状态机约束） */
    void changeStatus(Long id, Integer targetStatus);

    /** 删除合同（仅草稿可删） */
    void deleteContract(Long id);
}
