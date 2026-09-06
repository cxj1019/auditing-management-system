package com.accounting.firm.collection.service.impl;

import com.accounting.firm.collection.dto.CollectionSummaryVO;
import com.accounting.firm.collection.dto.PaymentRequest;
import com.accounting.firm.collection.dto.PaymentVO;
import com.accounting.firm.collection.entity.ContractPayment;
import com.accounting.firm.collection.mapper.ContractPaymentMapper;
import com.accounting.firm.collection.service.CollectionService;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.DataScopeService;
import com.accounting.firm.contract.entity.Contract;
import com.accounting.firm.contract.entity.ContractStatus;
import com.accounting.firm.contract.mapper.ContractMapper;
import com.accounting.firm.invoice.entity.Invoice;
import com.accounting.firm.invoice.entity.InvoiceStatus;
import com.accounting.firm.invoice.mapper.InvoiceMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 收款服务实现
 */
@Service
@RequiredArgsConstructor
public class CollectionServiceImpl extends ServiceImpl<ContractPaymentMapper, ContractPayment>
        implements CollectionService {

    private final ContractMapper contractMapper;
    private final InvoiceMapper invoiceMapper;
    private final DataScopeService dataScopeService;

    @Override
    public PageResult<PaymentVO> pagePayments(long current, long size, String keyword,
                                              LocalDate startDate, LocalDate endDate) {
        Page<?> page = new Page<>(current, size);
        var scope = dataScopeService.currentScope();
        List<PaymentVO> records = baseMapper.selectPaymentPage(page, keyword, startDate, endDate,
                scope.type() == DataScopeService.ScopeType.DEPT ? scope.deptId() : null,
                scope.type() == DataScopeService.ScopeType.SELF ? scope.username() : null).getRecords();
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void addPayment(PaymentRequest request) {
        ContractPayment payment = new ContractPayment();
        if (request.getInvoiceId() != null) {
            // 核销收款：挂已开票发票，合同由发票带出
            Invoice invoice = requireValidInvoice(request.getInvoiceId());
            payment.setInvoiceId(invoice.getId());
            payment.setContractId(invoice.getContractId());
        } else {
            // 预收收款：暂挂合同，开票后可核销到发票
            Contract contract = requireValidContract(request.getContractId());
            payment.setContractId(contract.getId());
        }
        copyFields(request, payment);
        save(payment);
    }

    @Override
    public void updatePayment(Long id, PaymentRequest request) {
        ContractPayment payment = getById(id);
        if (payment == null) {
            throw new BusinessException("收款记录不存在");
        }
        // 归属不可变更：忽略请求中的 invoiceId/contractId
        copyFields(request, payment);
        updateById(payment);
    }

    @Override
    public void writeOff(Long id, Long invoiceId) {
        ContractPayment payment = getById(id);
        if (payment == null) {
            throw new BusinessException("收款记录不存在");
        }
        if (payment.getInvoiceId() != null) {
            throw new BusinessException("该收款已核销到发票，不可重复核销");
        }
        Invoice invoice = requireValidInvoice(invoiceId);
        if (!invoice.getContractId().equals(payment.getContractId())) {
            throw new BusinessException("发票与收款不属于同一合同，不可核销");
        }
        payment.setInvoiceId(invoice.getId());
        updateById(payment);
    }

    @Override
    public void deletePayment(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("收款记录不存在");
        }
        removeById(id);
    }

    @Override
    public List<CollectionSummaryVO> summary(String keyword) {
        var scope = dataScopeService.currentScope();
        List<CollectionSummaryVO> rows = baseMapper.selectSummary(keyword,
                scope.type() == DataScopeService.ScopeType.DEPT ? scope.deptId() : null,
                scope.type() == DataScopeService.ScopeType.SELF ? scope.username() : null);
        rows.forEach(CollectionSummaryVO::fillDerived);
        return rows;
    }

    @Override
    public List<com.accounting.firm.collection.dto.RechargeLedgerVO> rechargeLedger() {
        var scope = dataScopeService.currentScope();
        List<com.accounting.firm.collection.dto.RechargeLedgerVO> rows = baseMapper.selectRechargeLedger(
                scope.type() == DataScopeService.ScopeType.DEPT ? scope.deptId() : null,
                scope.type() == DataScopeService.ScopeType.SELF ? scope.username() : null);
        rows.forEach(com.accounting.firm.collection.dto.RechargeLedgerVO::fillDerived);
        return rows;
    }

    /** 校验发票存在且已开票 */
    private Invoice requireValidInvoice(Long invoiceId) {
        if (invoiceId == null) {
            throw new BusinessException("所属发票不能为空");
        }
        Invoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new BusinessException("关联发票不存在");
        }
        if (invoice.getStatus() != InvoiceStatus.ISSUED.getCode()) {
            throw new BusinessException("仅已开票的发票可登记收款");
        }
        return invoice;
    }

    /** 校验合同存在且非草稿（预收收款挂合同） */
    private Contract requireValidContract(Long contractId) {
        if (contractId == null) {
            throw new BusinessException("所属合同不能为空");
        }
        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException("关联合同不存在");
        }
        if (contract.getStatus() == ContractStatus.DRAFT.getCode()) {
            throw new BusinessException("草稿状态的合同不能登记收款");
        }
        return contract;
    }

    /** 复制可编辑字段（不含发票/合同关联） */
    private void copyFields(PaymentRequest request, ContractPayment payment) {
        payment.setAmount(request.getAmount());
        payment.setPaymentDate(request.getPaymentDate());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPayerName(request.getPayerName());
        payment.setRemark(request.getRemark());
    }
}
