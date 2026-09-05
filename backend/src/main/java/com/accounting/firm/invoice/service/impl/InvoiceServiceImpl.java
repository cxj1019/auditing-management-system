package com.accounting.firm.invoice.service.impl;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.DataScopeService;
import com.accounting.firm.common.storage.SupabaseStorageService;
import com.accounting.firm.collection.entity.ContractPayment;
import com.accounting.firm.contract.entity.Contract;
import com.accounting.firm.contract.entity.ContractStatus;
import com.accounting.firm.contract.mapper.ContractMapper;
import com.accounting.firm.invoice.dto.InvoiceOptionVO;
import com.accounting.firm.invoice.dto.InvoiceRequest;
import com.accounting.firm.invoice.dto.InvoiceSummaryVO;
import com.accounting.firm.invoice.dto.InvoiceVO;
import com.accounting.firm.invoice.entity.Invoice;
import com.accounting.firm.invoice.entity.InvoiceAttachment;
import com.accounting.firm.invoice.entity.InvoiceStatus;
import com.accounting.firm.invoice.mapper.InvoiceAttachmentMapper;
import com.accounting.firm.invoice.mapper.InvoiceMapper;
import com.accounting.firm.invoice.service.InvoiceService;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 发票服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl extends ServiceImpl<InvoiceMapper, Invoice>
        implements InvoiceService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final ContractMapper contractMapper;
    private final ProjectMapper projectMapper;
    private final com.accounting.firm.system.mapper.BusinessTypeMapper businessTypeMapper;
    private final InvoiceAttachmentMapper attachmentMapper;
    private final com.accounting.firm.collection.mapper.ContractPaymentMapper paymentMapper;
    private final SupabaseStorageService storageService;
    private final DataScopeService dataScopeService;

    @Override
    public PageResult<InvoiceVO> pageInvoices(long current, long size, String keyword,
                                              String type, Integer status) {
        Page<?> page = new Page<>(current, size);
        var scope = dataScopeService.currentScope();
        var result = baseMapper.selectInvoicePage(page, keyword, type, status,
                scope.type() == DataScopeService.ScopeType.DEPT ? scope.deptId() : null,
                scope.type() == DataScopeService.ScopeType.SELF ? scope.username() : null);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public void createInvoice(InvoiceRequest request) {
        Contract contract = requireValidContract(request.getContractId());
        if (StringUtils.hasText(request.getInvoiceNo())) {
            checkInvoiceNoUnique(request.getInvoiceNo(), null);
        }
        Invoice invoice = new Invoice();
        invoice.setContractId(contract.getId());
        invoice.setClientId(resolveClientId(contract));
        copyFields(request, invoice);
        deriveFxAmount(invoice);
        deriveTaxSplit(invoice);
        fillInvoiceElements(contract, invoice);
        invoice.setStatus(InvoiceStatus.PENDING.getCode());
        save(invoice);
    }

    @Override
    public void updateInvoice(InvoiceRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("发票 ID 不能为空");
        }
        Invoice invoice = getById(request.getId());
        if (invoice == null) {
            throw new BusinessException("发票不存在");
        }
        if (invoice.getStatus() == InvoiceStatus.VOIDED.getCode()) {
            throw new BusinessException("已作废的发票不可编辑");
        }
        if (StringUtils.hasText(request.getInvoiceNo())) {
            checkInvoiceNoUnique(request.getInvoiceNo(), invoice.getId());
        }
        // 所属合同不可变更：忽略请求中的 contractId
        copyFields(request, invoice);
        deriveFxAmount(invoice);
        deriveTaxSplit(invoice);
        fillInvoiceElements(contractMapper.selectById(invoice.getContractId()), invoice);
        updateById(invoice);
    }

    @Override
    public void deleteInvoice(Long id) {
        Invoice invoice = getById(id);
        if (invoice == null) {
            throw new BusinessException("发票不存在");
        }
        if (invoice.getStatus() == InvoiceStatus.ISSUED.getCode()) {
            throw new BusinessException("已开票的发票不可删除，请先作废");
        }
        if (paymentCount(id) > 0) {
            throw new BusinessException("该发票已存在收款核销，不可删除");
        }
        removeById(id);
    }

    @Override
    public void changeStatus(Long id, String action, LocalDate invoiceDate) {
        Invoice invoice = getById(id);
        if (invoice == null) {
            throw new BusinessException("发票不存在");
        }
        InvoiceStatus current = InvoiceStatus.of(invoice.getStatus());
        switch (action == null ? "" : action) {
            case "issue" -> {
                if (invoiceDate == null) {
                    throw new BusinessException("开票日期不能为空");
                }
                if (!StringUtils.hasText(invoice.getInvoiceNo())) {
                    throw new BusinessException("开票前须填写发票号码，请先编辑补充");
                }
                current.transitionTo(InvoiceStatus.ISSUED);
                invoice.setStatus(InvoiceStatus.ISSUED.getCode());
                invoice.setInvoiceDate(invoiceDate);
            }
            case "void" -> {
                if (paymentCount(id) > 0) {
                    throw new BusinessException("该发票已存在收款核销，不可作废");
                }
                current.transitionTo(InvoiceStatus.VOIDED);
                invoice.setStatus(InvoiceStatus.VOIDED.getCode());
            }
            default -> throw new BusinessException("非法的流转动作");
        }
        updateById(invoice);
    }

    @Override
    public List<InvoiceOptionVO> options(String keyword) {
        var scope = dataScopeService.currentScope();
        List<InvoiceVO> issued = baseMapper.selectInvoicePage(
                new Page<>(1, 500), keyword, null, InvoiceStatus.ISSUED.getCode(),
                scope.type() == DataScopeService.ScopeType.DEPT ? scope.deptId() : null,
                scope.type() == DataScopeService.ScopeType.SELF ? scope.username() : null).getRecords();
        return issued.stream().map(vo -> {
            InvoiceOptionVO option = new InvoiceOptionVO();
            option.setId(vo.getId());
            option.setInvoiceNo(vo.getInvoiceNo());
            option.setContractId(vo.getContractId());
            option.setContractNo(vo.getContractNo());
            option.setContractName(vo.getContractName());
            option.setClientName(vo.getClientName());
            option.setAmount(vo.getAmount());
            option.setCollectedAmount(vo.getCollectedAmount());
            return option;
        }).toList();
    }

    @Override
    public List<InvoiceSummaryVO> summary(String keyword) {
        var scope = dataScopeService.currentScope();
        List<InvoiceSummaryVO> rows = baseMapper.selectInvoiceSummary(keyword,
                scope.type() == DataScopeService.ScopeType.DEPT ? scope.deptId() : null,
                scope.type() == DataScopeService.ScopeType.SELF ? scope.username() : null);
        rows.forEach(InvoiceSummaryVO::fillDerived);
        return rows;
    }

    // ---------- 附件（发票扫描件） ----------

    @Override
    public InvoiceAttachment uploadAttachment(Long invoiceId, MultipartFile file) {
        if (getById(invoiceId) == null) {
            throw new BusinessException("发票不存在");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过 20MB");
        }
        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("仅支持 PDF 与图片（jpg/png）格式");
        }
        String storedName = "invoices/" + invoiceId + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            storageService.upload(storedName, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败，请重试");
        }

        InvoiceAttachment att = new InvoiceAttachment();
        att.setInvoiceId(invoiceId);
        att.setAttachmentType("scan");
        att.setFileName(file.getOriginalFilename());
        att.setStoredName(storedName);
        att.setFileSize(file.getSize());
        att.setContentType(file.getContentType());
        attachmentMapper.insert(att);
        return att;
    }

    @Override
    public List<InvoiceAttachment> listAttachments(Long invoiceId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<InvoiceAttachment>()
                .eq(InvoiceAttachment::getInvoiceId, invoiceId)
                .orderByDesc(InvoiceAttachment::getCreateTime));
    }

    @Override
    public InvoiceAttachment getAttachment(Long invoiceId, Long attachmentId) {
        InvoiceAttachment att = attachmentMapper.selectById(attachmentId);
        if (att == null || !att.getInvoiceId().equals(invoiceId)) {
            throw new BusinessException("附件不存在");
        }
        return att;
    }

    @Override
    public byte[] downloadAttachment(InvoiceAttachment attachment) {
        return storageService.download(attachment.getStoredName());
    }

    @Override
    public void deleteAttachment(Long invoiceId, Long attachmentId) {
        InvoiceAttachment att = getAttachment(invoiceId, attachmentId);
        attachmentMapper.deleteById(attachmentId);
        try {
            storageService.delete(att.getStoredName());
        } catch (Exception e) {
            log.warn("发票附件远端对象清理失败: {}", att.getStoredName(), e);
        }
    }

    // ---------- 私有辅助 ----------

    /** 校验合同存在且非草稿 */
    private Contract requireValidContract(Long contractId) {
        if (contractId == null) {
            throw new BusinessException("所属合同不能为空");
        }
        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException("关联合同不存在");
        }
        if (contract.getStatus() == ContractStatus.DRAFT.getCode()) {
            throw new BusinessException("草稿状态的合同不能登记发票");
        }
        return contract;
    }

    /** 外币折算：非人民币且未填价税合计时，按 外币金额 ÷ 100 × 中行牌价 折算 */
    private void deriveFxAmount(Invoice invoice) {
        boolean isCny = invoice.getCurrency() == null || "人民币".equals(invoice.getCurrency());
        if (invoice.getAmount() != null || isCny
                || invoice.getForeignAmount() == null
                || invoice.getExchangeRate() == null) {
            return;
        }
        invoice.setAmount(invoice.getForeignAmount()
                .multiply(invoice.getExchangeRate())
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
    }

    /** 拆分推算：有税率且缺不含税金额时按 价税合计/(1+税率) 推算；缺税额时按 价税合计−不含税 推算 */
    private void deriveTaxSplit(Invoice invoice) {
        BigDecimal exTax = invoice.getAmountExTax();
        BigDecimal total = invoice.getAmount();
        BigDecimal tax = invoice.getTaxAmount();
        if (exTax == null && total == null) {
            throw new BusinessException("请填写不含税金额");
        }
        // 未填不含税金额时按价税合计反推（兼容旧调用）
        if (exTax == null) {
            if (tax != null) {
                exTax = total.subtract(tax);
            } else if (invoice.getTaxRate() != null) {
                exTax = total.divide(
                        BigDecimal.ONE.add(invoice.getTaxRate().divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP)),
                        2, java.math.RoundingMode.HALF_UP);
            } else {
                exTax = total;
            }
            invoice.setAmountExTax(exTax);
        }
        // 税额 = 不含税金额 × 税率%（未手动填写时）
        if (tax == null) {
            tax = invoice.getTaxRate() != null
                    ? exTax.multiply(invoice.getTaxRate()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            invoice.setTaxAmount(tax);
        }
        // 价税合计 = 不含税金额 + 税额（未手动填写时）
        if (total == null) {
            total = exTax.add(tax);
            invoice.setAmount(total);
        }
    }

    /** 开票要素（品名/税收编码/分类）为空时，按合同（或其项目）的业务类型从字典填充 */
    private void fillInvoiceElements(Contract contract, Invoice invoice) {
        if (contract == null) {
            return;
        }
        String bizType = StringUtils.hasText(contract.getBizType())
                ? contract.getBizType()
                : null;
        if (!StringUtils.hasText(bizType) && contract.getProjectId() != null) {
            Project project = projectMapper.selectById(contract.getProjectId());
            if (project != null && StringUtils.hasText(project.getBizType())) {
                bizType = project.getBizType();
            }
        }
        if (!StringUtils.hasText(bizType)) {
            return;
        }
        com.accounting.firm.system.entity.BusinessType bt = businessTypeMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.accounting.firm.system.entity.BusinessType>()
                        .eq(com.accounting.firm.system.entity.BusinessType::getBizType, bizType)
                        .last("LIMIT 1"));
        if (bt == null) {
            return;
        }
        if (!StringUtils.hasText(invoice.getInvoiceItem())) {
            invoice.setInvoiceItem(bt.getInvoiceItem());
        }
        if (!StringUtils.hasText(invoice.getTaxCode())) {
            invoice.setTaxCode(bt.getTaxCode());
        }
        if (!StringUtils.hasText(invoice.getTaxClass())) {
            invoice.setTaxClass(bt.getTaxClass());
        }
    }

    /** 客户 ID 冗余自合同所属项目 */
    private Long resolveClientId(Contract contract) {
        if (contract.getProjectId() == null) {
            return 0L;
        }
        Project project = projectMapper.selectById(contract.getProjectId());
        return project == null || project.getClientId() == null ? 0L : project.getClientId();
    }

    private void checkInvoiceNoUnique(String invoiceNo, Long excludeId) {
        Long count = lambdaQuery()
                .eq(Invoice::getInvoiceNo, invoiceNo)
                .ne(excludeId != null, Invoice::getId, excludeId)
                .count();
        if (count > 0) {
            throw new BusinessException("发票号码已存在");
        }
    }

    private long paymentCount(Long invoiceId) {
        Long count = paymentMapper.selectCount(
                new LambdaQueryWrapper<ContractPayment>()
                        .eq(ContractPayment::getInvoiceId, invoiceId));
        return count == null ? 0 : count;
    }

    private void copyFields(InvoiceRequest request, Invoice invoice) {
        invoice.setInvoiceNo(StringUtils.hasText(request.getInvoiceNo()) ? request.getInvoiceNo() : null);
        invoice.setCurrency(StringUtils.hasText(request.getCurrency()) ? request.getCurrency() : "人民币");
        invoice.setForeignAmount(request.getForeignAmount());
        invoice.setExchangeRate(request.getExchangeRate());
        invoice.setRatePublishTime(request.getRatePublishTime());
        invoice.setType(request.getType());
        invoice.setAmount(request.getAmount());
        invoice.setTaxRate(request.getTaxRate());
        invoice.setAmountExTax(request.getAmountExTax());
        invoice.setTaxAmount(request.getTaxAmount());
        invoice.setInvoiceItem(request.getInvoiceItem());
        invoice.setTaxCode(request.getTaxCode());
        invoice.setTaxClass(request.getTaxClass());
        invoice.setRemark(request.getRemark());
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
