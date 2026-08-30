package com.accounting.firm.collection.service;

import com.accounting.firm.collection.dto.CollectionSummaryVO;
import com.accounting.firm.collection.dto.PaymentRequest;
import com.accounting.firm.collection.dto.PaymentVO;
import com.accounting.firm.collection.entity.ContractPayment;
import com.accounting.firm.common.api.PageResult;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;

/**
 * 收款服务
 */
public interface CollectionService extends IService<ContractPayment> {

    /** 分页筛选查询收款记录 */
    PageResult<PaymentVO> pagePayments(long current, long size, String keyword,
                                       LocalDate startDate, LocalDate endDate);

    /** 登记收款（核销收款挂已开票发票；预收收款挂合同） */
    void addPayment(PaymentRequest request);

    /** 编辑收款（不可变更所属发票/合同） */
    void updatePayment(Long id, PaymentRequest request);

    /** 预收核销：将未核销收款关联到同一合同的已开票发票 */
    void writeOff(Long id, Long invoiceId);

    /** 删除收款 */
    void deletePayment(Long id);

    /** 按合同维度汇总收款（含未收余额与进度） */
    List<CollectionSummaryVO> summary(String keyword);
}
