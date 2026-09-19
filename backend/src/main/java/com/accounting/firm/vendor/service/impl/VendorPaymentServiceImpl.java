package com.accounting.firm.vendor.service.impl;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.DataScopeService;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.common.storage.SupabaseStorageService;
import com.accounting.firm.vendor.entity.VendorPayment;
import com.accounting.firm.vendor.entity.VendorPaymentAttachment;
import com.accounting.firm.vendor.mapper.VendorPaymentAttachmentMapper;
import com.accounting.firm.vendor.mapper.VendorPaymentMapper;
import com.accounting.firm.vendor.service.VendorPaymentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 对公付款实现：登记(草稿)→提交→审批→付款；部门隔离按经理及以上全量、员工仅本人
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorPaymentServiceImpl extends ServiceImpl<VendorPaymentMapper, VendorPayment>
        implements VendorPaymentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final VendorPaymentAttachmentMapper attachmentMapper;
    private final com.accounting.firm.vendor.mapper.VendorInvoiceMapper vendorInvoiceMapper;
    private final SupabaseStorageService storageService;
    private final DataScopeService dataScopeService;
    private final com.accounting.firm.project.mapper.ProjectMapper projectMapper;
    private final com.accounting.firm.contract.mapper.ContractMapper contractMapper;

    @Override
    public PageResult<VendorPayment> page(long current, long size, Integer status, String keyword, SecurityUser currentUser) {
        LambdaQueryWrapper<VendorPayment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, VendorPayment::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(VendorPayment::getVendorName, keyword)
                    .or().like(VendorPayment::getSummary, keyword)
                    .or().like(VendorPayment::getPaymentNo, keyword)
                    .or().like(VendorPayment::getInvoiceNo, keyword));
        }
        // 部门隔离：员工仅看自己登记的；经理/合伙人看本部门项目的付款 + 本部门人员登记的无项目付款 + 自己登记的；
        // admin/财务看全量
        int level = dataScopeService.roleLevel(currentUser.getUserId());
        boolean seeAll = currentUser.hasRole("admin") || currentUser.hasRole("finance");
        if (seeAll) {
            // admin/财务 全量
        } else if (level <= 1) {
            wrapper.eq(VendorPayment::getCreateBy, currentUser.getUsername());
        } else {
            Long deptId = currentUser.getDeptId();
            if (deptId != null) {
                wrapper.and(w -> w
                        .inSql(VendorPayment::getProjectId, "SELECT id FROM project WHERE dept_id = " + deptId)
                        .or(w2 -> w2.isNull(VendorPayment::getProjectId)
                                .inSql(VendorPayment::getCreateBy,
                                        "SELECT username FROM sys_user WHERE dept_id = " + deptId))
                        .or(w3 -> w3.eq(VendorPayment::getCreateBy, currentUser.getUsername())));
            } else {
                wrapper.eq(VendorPayment::getCreateBy, currentUser.getUsername());
            }
        }
        wrapper.orderByDesc(VendorPayment::getCreateTime);
        Page<VendorPayment> page = page(new Page<>(current, size), wrapper);
        List<VendorPayment> records = page.getRecords();
        fillNames(records);
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private void fillNames(List<VendorPayment> records) {
        List<Long> projectIds = records.stream()
                .map(VendorPayment::getProjectId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : projectMapper.selectBatchIds(projectIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.accounting.firm.project.entity.Project::getId,
                                com.accounting.firm.project.entity.Project::getName));
        List<Long> contractIds = records.stream()
                .map(VendorPayment::getContractId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> contractNos = contractIds.isEmpty() ? Map.of()
                : contractMapper.selectBatchIds(contractIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.accounting.firm.contract.entity.Contract::getId,
                                com.accounting.firm.contract.entity.Contract::getContractNo));
        for (VendorPayment v : records) {
            if (v.getProjectId() != null) v.setProjectName(projectNames.get(v.getProjectId()));
            if (v.getContractId() != null) v.setContractNo(contractNos.get(v.getContractId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(VendorPayment request, SecurityUser currentUser) {
        validateAmount(request);
        VendorPayment payment = new VendorPayment();
        copyFields(request, payment);
        payment.setPaymentNo(generateNo());
        payment.setStatus(0);
        payment.setCreateBy(currentUser.getUsername());
        payment.setCreatorName(currentUser.getNickname() != null ? currentUser.getNickname() : currentUser.getUsername());
        save(payment);
        return payment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(VendorPayment request, SecurityUser currentUser) {
        if (request.getId() == null) {
            throw new BusinessException("付款单 ID 不能为空");
        }
        VendorPayment payment = getById(request.getId());
        if (payment == null) {
            throw new BusinessException("付款单不存在");
        }
        if (payment.getStatus() != 0 && payment.getStatus() != 3) {
            throw new BusinessException("仅草稿或已驳回的付款单可编辑");
        }
        if (!currentUser.getUsername().equals(payment.getCreateBy())) {
            throw new BusinessException("仅登记人可以编辑");
        }
        validateAmount(request);
        Long id = request.getId();
        String paymentNo = payment.getPaymentNo();
        String createBy = payment.getCreateBy();
        String creatorName = payment.getCreatorName();
        copyFields(request, payment);
        payment.setId(id);
        payment.setPaymentNo(paymentNo);
        payment.setCreateBy(createBy);
        payment.setCreatorName(creatorName);
        updateById(payment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, SecurityUser currentUser) {
        VendorPayment payment = getById(id);
        if (payment == null) {
            throw new BusinessException("付款单不存在");
        }
        if (!currentUser.getUsername().equals(payment.getCreateBy())) {
            throw new BusinessException("仅登记人可以提交");
        }
        if (payment.getStatus() != 0 && payment.getStatus() != 3) {
            throw new BusinessException("仅草稿或已驳回的付款单可提交");
        }
        payment.setStatus(1);
        payment.setApproverName(null);
        payment.setApproveComment(null);
        payment.setApproveTime(null);
        updateById(payment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id, SecurityUser currentUser) {
        VendorPayment payment = getById(id);
        if (payment == null) {
            throw new BusinessException("付款单不存在");
        }
        if (!currentUser.getUsername().equals(payment.getCreateBy())) {
            throw new BusinessException("仅登记人可以撤回");
        }
        if (payment.getStatus() != 1) {
            throw new BusinessException("仅待审批的付款单可撤回");
        }
        payment.setStatus(0);
        updateById(payment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, SecurityUser currentUser) {
        VendorPayment payment = getById(id);
        if (payment == null) {
            throw new BusinessException("付款单不存在");
        }
        if (payment.getStatus() != 0 && payment.getStatus() != 3) {
            throw new BusinessException("仅草稿或已驳回的付款单可删除");
        }
        if (!currentUser.getUsername().equals(payment.getCreateBy())) {
            throw new BusinessException("仅登记人可以删除");
        }
        removeById(id);
        var atts = attachmentMapper.selectList(new LambdaQueryWrapper<VendorPaymentAttachment>()
                .eq(VendorPaymentAttachment::getPaymentId, id));
        for (var att : atts) {
            try {
                storageService.delete(att.getStoredName());
            } catch (Exception e) {
                log.warn("删除付款附件存储对象失败: {}", att.getStoredName());
            }
            attachmentMapper.deleteById(att.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String action, String comment, SecurityUser currentUser) {
        VendorPayment payment = getById(id);
        if (payment == null) {
            throw new BusinessException("付款单不存在");
        }
        if (currentUser.getUsername().equals(payment.getCreateBy())) {
            throw new BusinessException("不能审批自己登记的付款单");
        }
        if (payment.getStatus() != 1) {
            throw new BusinessException("仅待审批的付款单可以审批");
        }
        if ("approve".equals(action)) {
            payment.setStatus(2);
        } else if ("reject".equals(action)) {
            payment.setStatus(3);
        } else {
            throw new BusinessException("非法的审批动作");
        }
        payment.setApproverName(currentUser.getNickname() != null ? currentUser.getNickname() : currentUser.getUsername());
        payment.setApproveComment(comment);
        payment.setApproveTime(LocalDateTime.now());
        updateById(payment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(Long id, SecurityUser currentUser) {
        VendorPayment payment = getById(id);
        if (payment == null) {
            throw new BusinessException("付款单不存在");
        }
        if (payment.getStatus() != 2) {
            throw new BusinessException("仅已批准的付款单可标记付款");
        }
        payment.setStatus(4);
        payment.setPaidBy(currentUser.getNickname() != null ? currentUser.getNickname() : currentUser.getUsername());
        payment.setPaidTime(LocalDateTime.now());
        if (payment.getPaymentDate() == null) {
            payment.setPaymentDate(LocalDate.now());
        }
        updateById(payment);
    }

    private void validateAmount(VendorPayment request) {
        if (!StringUtils.hasText(request.getVendorName())) {
            throw new BusinessException("请填写供应商名称");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new BusinessException("请填写付款金额");
        }
        // 价税分离：有税率时推算不含税与税额
        BigDecimal amount = request.getAmount();
        BigDecimal rate = request.getTaxRate();
        if (rate != null && rate.signum() > 0) {
            if (request.getAmountExTax() == null) {
                BigDecimal ex = amount.divide(
                        BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP)),
                        2, java.math.RoundingMode.HALF_UP);
                request.setAmountExTax(ex);
            }
            if (request.getTaxAmount() == null) {
                request.setTaxAmount(amount.subtract(request.getAmountExTax()));
            }
        } else {
            request.setAmountExTax(amount);
            request.setTaxAmount(BigDecimal.ZERO);
            request.setTaxRate(null);
        }
    }

    private void copyFields(VendorPayment request, VendorPayment payment) {
        if (request.getVendorInvoiceId() != null) {
            requireValidInvoice(request.getVendorInvoiceId());
        }
        payment.setVendorInvoiceId(request.getVendorInvoiceId());
        payment.setVendorName(request.getVendorName().trim());
        payment.setSummary(request.getSummary());
        payment.setProjectId(request.getProjectId());
        payment.setContractId(request.getContractId());
        payment.setAmount(request.getAmount());
        payment.setTaxRate(request.getTaxRate());
        payment.setTaxAmount(request.getTaxAmount());
        payment.setAmountExTax(request.getAmountExTax());
        payment.setPaymentDate(request.getPaymentDate());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setInvoiceNo(StringUtils.hasText(request.getInvoiceNo()) ? request.getInvoiceNo().trim() : null);
    }

    /** 付款编号：FK + 年份 + 4 位流水（按年份独立递增） */
    private void requireValidInvoice(Long invoiceId) {
        if (vendorInvoiceMapper.selectById(invoiceId) == null) {
            throw new BusinessException("关联的进项发票不存在");
        }
    }

    /** 预付款核销到进项发票 */
    @Override
    public void writeOff(Long id, Long invoiceId, SecurityUser currentUser) {
        VendorPayment payment = getById(id);
        if (payment == null) {
            throw new BusinessException("付款单不存在");
        }
        requireValidInvoice(invoiceId);
        payment.setVendorInvoiceId(invoiceId);
        updateById(payment);
    }

    @Override
    public List<com.accounting.firm.vendor.entity.VendorInvoice> listInvoices(String keyword) {
        LambdaQueryWrapper<com.accounting.firm.vendor.entity.VendorInvoice> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(com.accounting.firm.vendor.entity.VendorInvoice::getVendorName, keyword)
                    .or().like(com.accounting.firm.vendor.entity.VendorInvoice::getInvoiceNo, keyword));
        }
        wrapper.orderByDesc(com.accounting.firm.vendor.entity.VendorInvoice::getId);
        return vendorInvoiceMapper.selectList(wrapper);
    }

    @Override
    public java.util.Map<String, Object> listInvoicesWithPaid(String keyword) {
        List<com.accounting.firm.vendor.entity.VendorInvoice> invoices = listInvoices(keyword);
        // 已核销金额：按发票聚合付款
        Map<Long, BigDecimal> paidByInvoice = new java.util.LinkedHashMap<>();
        for (VendorPayment p : list()) {
            if (p.getVendorInvoiceId() != null) {
                paidByInvoice.merge(p.getVendorInvoiceId(), nvl(p.getAmount()), BigDecimal::add);
            }
        }
        List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (com.accounting.firm.vendor.entity.VendorInvoice inv : invoices) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", inv.getId());
            row.put("vendorName", inv.getVendorName());
            row.put("invoiceNo", inv.getInvoiceNo());
            row.put("type", inv.getType());
            row.put("taxRate", inv.getTaxRate());
            row.put("amount", inv.getAmount());
            row.put("amountExTax", inv.getAmountExTax());
            row.put("taxAmount", inv.getTaxAmount());
            row.put("invoiceDate", inv.getInvoiceDate());
            row.put("projectId", inv.getProjectId());
            row.put("remark", inv.getRemark());
            row.put("paidAmount", paidByInvoice.getOrDefault(inv.getId(), BigDecimal.ZERO));
            if (inv.getProjectId() != null) {
                var pj = projectMapper.selectById(inv.getProjectId());
                row.put("projectName", pj == null ? null : pj.getName());
            }
            rows.add(row);
        }
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("rows", rows);
        return data;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInvoice(com.accounting.firm.vendor.entity.VendorInvoice invoice, SecurityUser currentUser) {
        invoice.setId(null);
        invoice.setCreateBy(currentUser.getUsername());
        invoice.setCreatorName(currentUser.getNickname() != null ? currentUser.getNickname() : currentUser.getUsername());
        vendorInvoiceMapper.insert(invoice);
        return invoice.getId();
    }

    @Override
    public void updateInvoice(com.accounting.firm.vendor.entity.VendorInvoice invoice) {
        if (invoice.getId() == null) {
            throw new BusinessException("进项发票 ID 不能为空");
        }
        if (vendorInvoiceMapper.selectById(invoice.getId()) == null) {
            throw new BusinessException("进项发票不存在");
        }
        vendorInvoiceMapper.updateById(invoice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInvoice(Long id) {
        Long refs = lambdaQuery().eq(VendorPayment::getVendorInvoiceId, id).count();
        if (refs != null && refs > 0) {
            throw new BusinessException("该进项发票已被 " + refs + " 笔付款核销，先取消关联再删除");
        }
        vendorInvoiceMapper.deleteById(id);
    }

    private String generateNo() {
        String year = String.valueOf(LocalDate.now().getYear());
        String prefix = "FK" + year;
        Long count = lambdaQuery().likeRight(VendorPayment::getPaymentNo, prefix).count();
        return prefix + String.format(Locale.ROOT, "%04d", count + 1);
    }

    // ---------- 附件 ----------

    @Override
    public List<VendorPaymentAttachment> listAttachments(Long paymentId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<VendorPaymentAttachment>()
                .eq(VendorPaymentAttachment::getPaymentId, paymentId)
                .orderByAsc(VendorPaymentAttachment::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VendorPaymentAttachment uploadAttachment(Long paymentId, MultipartFile file, SecurityUser currentUser) {
        VendorPayment payment = getById(paymentId);
        if (payment == null) {
            throw new BusinessException("付款单不存在");
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
            throw new BusinessException("附件仅支持 PDF 与图片（jpg/png）格式");
        }
        String storedName = "vendor-payments/" + paymentId + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            storageService.upload(storedName, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            throw new BusinessException("读取上传文件失败，请重试");
        }
        VendorPaymentAttachment attachment = new VendorPaymentAttachment();
        attachment.setPaymentId(paymentId);
        attachment.setFileName(original);
        attachment.setStoredName(storedName);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(file.getContentType());
        attachment.setCreateBy(currentUser.getUsername());
        attachmentMapper.insert(attachment);
        return attachment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAttachment(Long paymentId, Long attachmentId, SecurityUser currentUser) {
        VendorPaymentAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !attachment.getPaymentId().equals(paymentId)) {
            throw new BusinessException("附件不存在");
        }
        // 登记人或审批人可删
        boolean owner = currentUser.getUsername().equals(attachment.getCreateBy());
        if (!owner && levelOf(currentUser) <= 1) {
            throw new BusinessException("没有权限删除该附件");
        }
        try {
            storageService.delete(attachment.getStoredName());
        } catch (Exception e) {
            log.warn("删除存储对象失败: {}", attachment.getStoredName());
        }
        attachmentMapper.deleteById(attachmentId);
    }

    @Override
    public byte[] downloadAttachment(Long attachmentId) {
        VendorPaymentAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        return storageService.download(attachment.getStoredName());
    }

    private int levelOf(SecurityUser user) {
        return dataScopeService.roleLevel(user.getUserId());
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
