package com.accounting.firm.common.backup;

import com.accounting.firm.client.entity.Client;
import com.accounting.firm.client.mapper.ClientMapper;
import com.accounting.firm.common.storage.SupabaseStorageService;
import com.accounting.firm.contract.entity.Contract;
import com.accounting.firm.contract.mapper.ContractMapper;
import com.accounting.firm.confirmation.entity.Confirmation;
import com.accounting.firm.confirmation.mapper.ConfirmationMapper;
import com.accounting.firm.invoice.entity.Invoice;
import com.accounting.firm.invoice.mapper.InvoiceMapper;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.accounting.firm.reimbursement.mapper.ReimbursementMapper;
import com.accounting.firm.schedule.entity.Schedule;
import com.accounting.firm.schedule.mapper.ScheduleMapper;
import com.accounting.firm.vendor.entity.VendorInvoice;
import com.accounting.firm.vendor.entity.VendorPayment;
import com.accounting.firm.vendor.mapper.VendorInvoiceMapper;
import com.accounting.firm.vendor.mapper.VendorPaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 每日自动备份：关键业务表导出 JSON 上传到对象存储（backups/ 目录）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final ClientMapper clientMapper;
    private final ProjectMapper projectMapper;
    private final ContractMapper contractMapper;
    private final InvoiceMapper invoiceMapper;
    private final ReimbursementMapper reimbursementMapper;
    private final ConfirmationMapper confirmationMapper;
    private final ScheduleMapper scheduleMapper;
    private final VendorPaymentMapper vendorPaymentMapper;
    private final VendorInvoiceMapper vendorInvoiceMapper;
    private final SupabaseStorageService storageService;

    /** 每日北京时间 02:30（UTC 18:30）自动备份 */
    @Scheduled(cron = "0 30 18 * * ?")
    public void dailyBackup() {
        try {
            runBackup();
        } catch (Exception e) {
            log.error("每日备份失败: {}", e.getMessage(), e);
        }
    }

    /** 执行备份并返回存储路径 */
    public String runBackup() throws Exception {
        String date = LocalDate.now().toString();
        Map<String, Object> dump = new LinkedHashMap<>();
        dump.put("backupTime", LocalDateTime.now().toString());
        dump.put("client", clientMapper.selectList(null));
        dump.put("project", projectMapper.selectList(null));
        dump.put("contract", contractMapper.selectList(null));
        dump.put("invoice", invoiceMapper.selectList(null));
        dump.put("reimbursement", reimbursementMapper.selectList(null));
        dump.put("confirmation", confirmationMapper.selectList(null));
        dump.put("schedule", scheduleMapper.selectList(null));
        dump.put("vendor_payment", vendorPaymentMapper.selectList(null));
        dump.put("vendor_invoice", vendorInvoiceMapper.selectList(null));

        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter().writeValueAsString(dump);
        String path = "backups/backup-" + date + "-" + System.currentTimeMillis() + ".json";
        storageService.upload(path, json.getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json");
        log.info("备份完成: {}", path);
        return path;
    }
}
