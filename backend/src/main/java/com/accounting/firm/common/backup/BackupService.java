package com.accounting.firm.common.backup;

import com.accounting.firm.client.entity.Client;
import com.accounting.firm.client.mapper.ClientMapper;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.storage.SupabaseStorageService;
import com.accounting.firm.confirmation.entity.Confirmation;
import com.accounting.firm.confirmation.entity.ConfirmationAttachment;
import com.accounting.firm.confirmation.mapper.ConfirmationAttachmentMapper;
import com.accounting.firm.confirmation.mapper.ConfirmationMapper;
import com.accounting.firm.contract.entity.Contract;
import com.accounting.firm.contract.mapper.ContractMapper;
import com.accounting.firm.invoice.entity.Invoice;
import com.accounting.firm.invoice.entity.InvoiceAttachment;
import com.accounting.firm.invoice.mapper.InvoiceAttachmentMapper;
import com.accounting.firm.invoice.mapper.InvoiceMapper;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.accounting.firm.reimbursement.entity.ReimbursementAttachment;
import com.accounting.firm.reimbursement.mapper.ReimbursementAttachmentMapper;
import com.accounting.firm.reimbursement.mapper.ReimbursementMapper;
import com.accounting.firm.schedule.entity.Schedule;
import com.accounting.firm.schedule.mapper.ScheduleMapper;
import com.accounting.firm.vendor.entity.VendorInvoice;
import com.accounting.firm.vendor.entity.VendorPayment;
import com.accounting.firm.vendor.entity.VendorPaymentAttachment;
import com.accounting.firm.vendor.mapper.VendorInvoiceMapper;
import com.accounting.firm.vendor.mapper.VendorPaymentAttachmentMapper;
import com.accounting.firm.vendor.mapper.VendorPaymentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 每日完整备份：全部业务表 JSON + 全部附件文件，打成一个 ZIP 上传到对象存储 backups/ 目录。
 * 保留策略：每日备份保留 30 天；每月 1 号的备份视为月备保留 1 年。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final ClientMapper clientMapper;
    private final ProjectMapper projectMapper;
    private final ContractMapper contractMapper;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceAttachmentMapper invoiceAttachmentMapper;
    private final ReimbursementMapper reimbursementMapper;
    private final ReimbursementAttachmentMapper reimbursementAttachmentMapper;
    private final ConfirmationMapper confirmationMapper;
    private final ConfirmationAttachmentMapper confirmationAttachmentMapper;
    private final ScheduleMapper scheduleMapper;
    private final VendorPaymentMapper vendorPaymentMapper;
    private final VendorInvoiceMapper vendorInvoiceMapper;
    private final VendorPaymentAttachmentMapper vendorPaymentAttachmentMapper;
    private final com.accounting.firm.collection.mapper.ContractPaymentMapper paymentMapper;
    private final com.accounting.firm.system.mapper.SysUserMapper sysUserMapper;
    private final com.accounting.firm.system.mapper.SysRoleMapper sysRoleMapper;
    private final com.accounting.firm.system.mapper.SysMenuMapper sysMenuMapper;
    private final com.accounting.firm.system.mapper.SysDepartmentMapper sysDepartmentMapper;
    private final com.accounting.firm.system.mapper.SysUserRoleMapper sysUserRoleMapper;
    private final com.accounting.firm.system.mapper.SysRoleMenuMapper sysRoleMenuMapper;
    private final com.accounting.firm.system.mapper.StaffLevelMapper staffLevelMapper;
    private final com.accounting.firm.common.backup.mapper.BackupHistoryMapper backupHistoryMapper;
    private final SupabaseStorageService storageService;

    /** 每日北京时间 02:30（UTC 18:30）自动备份 */
    @Scheduled(cron = "0 30 18 * * ?")
    public void dailyBackup() {
        try {
            runBackup("system");
        } catch (Exception e) {
            log.error("每日备份失败: {}", e.getMessage(), e);
        }
    }

    /** 执行完整备份（表数据 JSON + 全部附件文件），返回对象路径与统计 */
    public Map<String, Object> runBackup(String operator) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int fileCount = 0;
        int tableCount = 0;

        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            // ---- 1) 表数据 JSON ----
            Map<String, Object> tables = new LinkedHashMap<>();
            tables.put("client", clientMapper.selectList(null));
            tables.put("project", projectMapper.selectList(null));
            tables.put("contract", contractMapper.selectList(null));
            tables.put("invoice", invoiceMapper.selectList(null));
            tables.put("invoice_attachment", invoiceAttachmentMapper.selectList(null));
            tables.put("reimbursement", reimbursementMapper.selectList(null));
            tables.put("reimbursement_attachment", reimbursementAttachmentMapper.selectList(null));
            tables.put("confirmation", confirmationMapper.selectList(null));
            tables.put("confirmation_attachment", confirmationAttachmentMapper.selectList(null));
            tables.put("schedule", scheduleMapper.selectList(null));
            tables.put("vendor_payment", vendorPaymentMapper.selectList(null));
            tables.put("vendor_invoice", vendorInvoiceMapper.selectList(null));
            tables.put("vendor_payment_attachment", vendorPaymentAttachmentMapper.selectList(null));
            tables.put("contract_payment", paymentMapper.selectList(null));
            tables.put("sys_user", sysUserMapper.selectList(null));
            tables.put("sys_role", sysRoleMapper.selectList(null));
            tables.put("sys_menu", sysMenuMapper.selectList(null));
            tables.put("sys_department", sysDepartmentMapper.selectList(null));
            tables.put("sys_user_role", sysUserRoleMapper.selectList(null));
            tables.put("sys_role_menu", sysRoleMenuMapper.selectList(null));
            tables.put("staff_level", staffLevelMapper.selectList(null));

            for (var entry : tables.entrySet()) {
                zip.putNextEntry(new ZipEntry("data/" + entry.getKey() + ".json"));
                zip.write(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(entry.getValue())
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                tableCount++;
            }

            // ---- 2) 全部附件文件（按 4 张附件表的存储路径取回） ----
            Map<String, Long> storedFiles = new LinkedHashMap<>();
            for (var a : invoiceAttachmentMapper.selectList(null)) {
                storedFiles.putIfAbsent(a.getStoredName(), a.getFileSize());
            }
            for (var a : reimbursementAttachmentMapper.selectList(null)) {
                storedFiles.putIfAbsent(a.getStoredName(), a.getFileSize());
            }
            for (var a : confirmationAttachmentMapper.selectList(null)) {
                storedFiles.putIfAbsent(a.getStoredName(), a.getFileSize());
            }
            for (var a : vendorPaymentAttachmentMapper.selectList(null)) {
                storedFiles.putIfAbsent(a.getStoredName(), a.getFileSize());
            }
            for (var stored : storedFiles.entrySet()) {
                try {
                    byte[] content = storageService.download(stored.getKey());
                    zip.putNextEntry(new ZipEntry("files/" + stored.getKey()));
                    zip.write(content);
                    zip.closeEntry();
                    fileCount++;
                } catch (Exception e) {
                    log.warn("备份附件 {} 失败，跳过: {}", stored.getKey(), e.getMessage());
                }
            }

            // ---- 3) 清单 ----
            String manifest = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    Map.of("backupTime", LocalDateTime.now().toString(), "tables", tableCount, "files", fileCount));
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        // ---- 上传 ----
        String path = "backups/backup-" + LocalDate.now() + "-" + System.currentTimeMillis() + ".zip";
        byte[] zipBytes = bos.toByteArray();
        storageService.upload(path, zipBytes, "application/zip");

        // ---- 历史 + 保留策略 ----
        var history = new com.accounting.firm.common.backup.BackupHistory();
        history.setObjectPath(path);
        history.setSizeBytes((long) zipBytes.length);
        history.setFileCount(fileCount);
        backupHistoryMapper.insert(history);
        applyRetention();

        log.info("备份完成: {} ({} KB, {} 表, {} 附件)", path, zipBytes.length / 1024, tableCount, fileCount);
        return Map.of("path", path, "sizeBytes", zipBytes.length, "tables", tableCount, "files", fileCount);
    }

    /** 保留策略：日备 30 天；每月 1 号的月备 1 年 */
    private void applyRetention() {
        var now = java.time.LocalDateTime.now();
        for (var row : backupHistoryMapper.selectList(new LambdaQueryWrapper<com.accounting.firm.common.backup.BackupHistory>()
                .orderByAsc(com.accounting.firm.common.backup.BackupHistory::getCreateTime))) {
            if (row.getCreateTime() == null) continue;
            boolean isMonthly = row.getCreateTime().getDayOfMonth() == 1;
            long days = java.time.temporal.ChronoUnit.DAYS.between(row.getCreateTime(), now);
            boolean expired = isMonthly ? days > 365 : days > 30;
            if (expired) {
                try {
                    storageService.delete(row.getObjectPath());
                } catch (Exception e) {
                    log.warn("删除过期备份 {} 失败: {}", row.getObjectPath(), e.getMessage());
                }
                backupHistoryMapper.deleteById(row.getId());
            }
        }
    }

    /** 备份历史（供管理页展示） */
    public List<com.accounting.firm.common.backup.BackupHistory> history() {
        return backupHistoryMapper.selectList(new LambdaQueryWrapper<com.accounting.firm.common.backup.BackupHistory>()
                .orderByDesc(com.accounting.firm.common.backup.BackupHistory::getCreateTime)
                .last("LIMIT 60"));
    }
}
