package com.accounting.firm.common.retention;

import com.accounting.firm.common.aop.entity.SysAuditLog;
import com.accounting.firm.common.aop.mapper.SysAuditLogMapper;
import com.accounting.firm.common.storage.SupabaseStorageService;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.accounting.firm.reimbursement.entity.ReimbursementAttachment;
import com.accounting.firm.reimbursement.mapper.ReimbursementAttachmentMapper;
import com.accounting.firm.reimbursement.mapper.ReimbursementMapper;
import com.accounting.firm.vendor.entity.VendorPayment;
import com.accounting.firm.vendor.entity.VendorPaymentAttachment;
import com.accounting.firm.vendor.mapper.VendorPaymentAttachmentMapper;
import com.accounting.firm.vendor.mapper.VendorPaymentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据保留策略（每日执行，避开 02:30 备份窗口）：
 * <ul>
 *   <li>审计日志保留 180 天</li>
 *   <li>回收站（软删除的报销单/付款单）保留 30 天，过期连同附件与存储对象彻底清除</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    static final int AUDIT_LOG_KEEP_DAYS = 180;
    static final int RECYCLE_KEEP_DAYS = 30;

    private final SysAuditLogMapper auditLogMapper;
    private final ReimbursementMapper reimbursementMapper;
    private final ReimbursementAttachmentMapper reimbursementAttachmentMapper;
    private final VendorPaymentMapper vendorPaymentMapper;
    private final VendorPaymentAttachmentMapper vendorPaymentAttachmentMapper;
    private final SupabaseStorageService storageService;

    @Scheduled(cron = "0 40 18 * * ?")
    public void scheduledCleanup() {
        try {
            cleanup();
        } catch (Exception e) {
            log.error("保留策略清理失败", e);
        }
    }

    /** 执行一次清理（定时任务与管理员手动触发共用），返回摘要 */
    public String cleanup() {
        int logs = purgeAuditLogs();
        int bills = purgeRecycleBin();
        String summary = "清理完成：审计日志删除 " + logs + " 条，回收站彻底清除单据 " + bills + " 张";
        log.info(summary);
        return summary;
    }

    private int purgeAuditLogs() {
        return auditLogMapper.delete(new LambdaQueryWrapper<SysAuditLog>()
                .lt(SysAuditLog::getCreateTime, LocalDateTime.now().minusDays(AUDIT_LOG_KEEP_DAYS)));
    }

    private int purgeRecycleBin() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RECYCLE_KEEP_DAYS);
        int total = 0;
        total += purgeReimbursements(cutoff);
        total += purgeVendorPayments(cutoff);
        return total;
    }

    private int purgeReimbursements(LocalDateTime cutoff) {
        List<Reimbursement> expired = reimbursementMapper.selectExpiredDeleted(cutoff);
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> ids = expired.stream().map(Reimbursement::getId).toList();
        List<ReimbursementAttachment> atts = reimbursementAttachmentMapper.selectList(
                new LambdaQueryWrapper<ReimbursementAttachment>()
                        .in(ReimbursementAttachment::getReimbursementId, ids));
        deleteStorageObjects(atts.stream().map(ReimbursementAttachment::getStoredName).toList());
        reimbursementAttachmentMapper.delete(new LambdaQueryWrapper<ReimbursementAttachment>()
                .in(ReimbursementAttachment::getReimbursementId, ids));
        reimbursementMapper.purgeByIds(ids);
        return ids.size();
    }

    private int purgeVendorPayments(LocalDateTime cutoff) {
        List<VendorPayment> expired = vendorPaymentMapper.selectExpiredDeleted(cutoff);
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> ids = expired.stream().map(VendorPayment::getId).toList();
        List<VendorPaymentAttachment> atts = vendorPaymentAttachmentMapper.selectList(
                new LambdaQueryWrapper<VendorPaymentAttachment>()
                        .in(VendorPaymentAttachment::getPaymentId, ids));
        deleteStorageObjects(atts.stream().map(VendorPaymentAttachment::getStoredName).toList());
        vendorPaymentAttachmentMapper.delete(new LambdaQueryWrapper<VendorPaymentAttachment>()
                .in(VendorPaymentAttachment::getPaymentId, ids));
        vendorPaymentMapper.purgeByIds(ids);
        return ids.size();
    }

    private void deleteStorageObjects(List<String> storedNames) {
        for (String name : storedNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                storageService.delete(name);
            } catch (Exception e) {
                log.warn("清理回收站附件存储对象失败: {}", name);
            }
        }
    }
}
