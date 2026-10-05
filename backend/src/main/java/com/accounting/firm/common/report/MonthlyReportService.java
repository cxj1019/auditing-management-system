package com.accounting.firm.common.report;

import com.accounting.firm.client.entity.Client;
import com.accounting.firm.client.mapper.ClientMapper;
import com.accounting.firm.collection.entity.ContractPayment;
import com.accounting.firm.collection.mapper.ContractPaymentMapper;
import com.accounting.firm.common.ai.AppSettingService;
import com.accounting.firm.common.mail.MailService;
import com.accounting.firm.confirmation.mapper.ConfirmationMapper;
import com.accounting.firm.contract.entity.Contract;
import com.accounting.firm.contract.mapper.ContractMapper;
import com.accounting.firm.invoice.entity.Invoice;
import com.accounting.firm.invoice.mapper.InvoiceMapper;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.accounting.firm.reimbursement.entity.ReimbursementItem;
import com.accounting.firm.reimbursement.mapper.ReimbursementItemMapper;
import com.accounting.firm.reimbursement.mapper.ReimbursementMapper;
import com.accounting.firm.schedule.entity.Schedule;
import com.accounting.firm.schedule.mapper.ScheduleMapper;
import com.accounting.firm.vendor.entity.VendorPayment;
import com.accounting.firm.vendor.mapper.VendorPaymentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 月度经营月报：收入（回款价税分离）/开票/费用成本/人工/工时/函证/应收账龄，
 * 汇总为 Excel，每月 1 日 08:00（北京时间）自动邮件，也可手动下载/发送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportService {

    private final ContractPaymentMapper paymentMapper;
    private final InvoiceMapper invoiceMapper;
    private final ReimbursementMapper reimbursementMapper;
    private final ReimbursementItemMapper reimbursementItemMapper;
    private final VendorPaymentMapper vendorPaymentMapper;
    private final ScheduleMapper scheduleMapper;
    private final ConfirmationMapper confirmationMapper;
    private final ClientMapper clientMapper;
    private final ProjectMapper projectMapper;
    private final ContractMapper contractMapper;
    private final AppSettingService appSettingService;
    private final MailService mailService;

    /** 每月 1 日 00:00 UTC（08:00 北京）发送上月月报 */
    @Scheduled(cron = "0 0 0 1 * ?")
    public void monthlyMail() {
        try {
            String to = appSettingService.get("report_mail_to");
            if (to == null || to.isBlank()) {
                log.info("未配置 report_mail_to，跳过月报邮件");
                return;
            }
            YearMonth last = YearMonth.now().minusMonths(1);
            String result = mailMonthly(last, "system");
            log.info("月报邮件: {}", result);
        } catch (Exception e) {
            log.error("月报邮件失败: {}", e.getMessage(), e);
        }
    }

    /** 生成上月月报并发送到 report_mail_to */
    public String mailMonthly(YearMonth month, String operator) throws Exception {
        String to = appSettingService.get("report_mail_to");
        if (to == null || to.isBlank()) {
            throw new IllegalStateException("请先在系统设置中配置月报接收邮箱");
        }
        byte[] xlsx = generateMonthly(month);
        String fileName = "经营月报_" + month + ".xlsx";
        mailService.sendWithAttachment(to.trim(), "【审计管理系统】经营月报 " + month,
                "附件为 " + month + " 月度经营月报（收入/开票/成本/工时/函证/应收账龄）。", fileName, xlsx);
        return "已发送 " + fileName + "（" + (xlsx.length / 1024) + " KB）至 " + to.trim();
    }

    /** 生成月报 Excel */
    public byte[] generateMonthly(YearMonth month) throws Exception {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            // ---------- 概览 ----------
            Sheet overview = wb.createSheet("概览");
            BigDecimal collectedEx = BigDecimal.ZERO;
            int payCount = 0;
            Map<Long, Invoice> invoiceMap = toMap(invoiceMapper.selectList(null), Invoice::getId);
            Map<Long, Contract> contractMap = toMap(contractMapper.selectList(null), Contract::getId);
            for (ContractPayment p : paymentMapper.selectList(null)) {
                if (p.getPaymentDate() == null || p.getPaymentDate().isBefore(start) || p.getPaymentDate().isAfter(end)) {
                    continue;
                }
                payCount++;
                collectedEx = collectedEx.add(exTax(p.getAmount(), p.getInvoiceId() == null ? null : invoiceMap.get(p.getInvoiceId()),
                        p.getInvoiceId() == null || invoiceMap.get(p.getInvoiceId()) == null ? null
                                : contractMap.get(invoiceMap.get(p.getInvoiceId()).getContractId())));
            }
            BigDecimal invoiced = BigDecimal.ZERO;
            int invoiceCount = 0;
            for (Invoice inv : invoiceMapper.selectList(null)) {
                if (inv.getInvoiceDate() == null || inv.getInvoiceDate().isBefore(start) || inv.getInvoiceDate().isAfter(end)) {
                    continue;
                }
                invoiceCount++;
                invoiced = invoiced.add(nvl(inv.getAmount()));
            }
            BigDecimal reimbCost = BigDecimal.ZERO;
            Map<Long, Reimbursement> reimbMap = toMap(reimbursementMapper.selectList(null), Reimbursement::getId);
            for (ReimbursementItem item : reimbursementItemMapper.selectList(null)) {
                if (item.getExpenseDate() == null || item.getExpenseDate().isBefore(start) || item.getExpenseDate().isAfter(end)) {
                    continue;
                }
                Reimbursement bill = reimbMap.get(item.getReimbursementId());
                if (bill == null || bill.getStatus() == null || (bill.getStatus() != 2 && bill.getStatus() != 4)) {
                    continue;
                }
                BigDecimal ex;
                if (item.getInvoiceType() != null && item.getInvoiceType().equals("vat_special") && item.getTaxRate() != null) {
                    BigDecimal amount = item.getAmount() == null ? BigDecimal.ZERO : item.getAmount();
                    ex = amount.subtract(item.getTaxAmount() != null ? item.getTaxAmount()
                            : amount.multiply(item.getTaxRate()).divide(new BigDecimal(100 + item.getTaxRate().doubleValue()), 2, RoundingMode.HALF_UP));
                } else {
                    ex = item.getAmount() == null ? BigDecimal.ZERO : item.getAmount();
                }
                reimbCost = reimbCost.add(ex);
            }
            BigDecimal vendorCost = BigDecimal.ZERO;
            for (VendorPayment vp : vendorPaymentMapper.selectList(null)) {
                if (vp.getPaymentDate() == null || vp.getPaymentDate().isBefore(start) || vp.getPaymentDate().isAfter(end)
                        || vp.getStatus() == null || (vp.getStatus() != 2 && vp.getStatus() != 4)) {
                    continue;
                }
                vendorCost = vendorCost.add(vp.getAmountExTax() != null ? vp.getAmountExTax() : nvl(vp.getAmount()));
            }
            BigDecimal hours = BigDecimal.ZERO;
            for (Schedule s : scheduleMapper.selectList(null)) {
                if (s.getScheduleDate() == null || s.getScheduleDate().isBefore(start) || s.getScheduleDate().isAfter(end)) {
                    continue;
                }
                hours = hours.add(s.getHours() == null ? BigDecimal.ZERO : s.getHours());
            }
            long confNew = confirmationMapper.selectList(null).stream()
                    .filter(c -> c.getCreateTime() != null
                            && !c.getCreateTime().toLocalDate().isBefore(start)
                            && !c.getCreateTime().toLocalDate().isAfter(end)).count();
            long confSent = confirmationMapper.selectList(null).stream()
                    .filter(c -> c.getStatus() != null && c.getStatus() >= 1 && c.getStatus() != 3).count();
            long confDone = confirmationMapper.selectList(null).stream()
                    .filter(c -> c.getStatus() != null && c.getStatus() == 2).count();

            int r = 0;
            row(overview, r++, "经营月报 " + month);
            row(overview, r++, "当月回款笔数", String.valueOf(payCount));
            row(overview, r++, "当月回款（不含税）", collectedEx.toPlainString());
            row(overview, r++, "当月开票张数", String.valueOf(invoiceCount));
            row(overview, r++, "当月开票（含税）", invoiced.toPlainString());
            row(overview, r++, "报销成本（不含税，已批准）", reimbCost.toPlainString());
            row(overview, r++, "对公付款成本（不含税，已批准/已付）", vendorCost.toPlainString());
            row(overview, r++, "当月工时（小时）", hours.toPlainString());
            row(overview, r++, "当月新增函证", String.valueOf(confNew));
            row(overview, r++, "全部函证 已发出/已回函", confSent + " / " + confDone);

            // ---------- 回款明细 ----------
            Sheet paySheet = wb.createSheet("回款明细");
            int pr = 0;
            Row head = paySheet.createRow(pr++);
            head.createCell(0).setCellValue("日期");
            head.createCell(1).setCellValue("发票号");
            head.createCell(2).setCellValue("客户");
            head.createCell(3).setCellValue("金额（含税）");
            head.createCell(4).setCellValue("金额（不含税）");
            for (ContractPayment p : paymentMapper.selectList(null)) {
                if (p.getPaymentDate() == null || p.getPaymentDate().isBefore(start) || p.getPaymentDate().isAfter(end)) {
                    continue;
                }
                Invoice inv = p.getInvoiceId() == null ? null : invoiceMap.get(p.getInvoiceId());
                Contract contract = inv == null ? null : contractMap.get(inv.getContractId());
                String clientName = "";
                if (inv != null && inv.getClientId() != null) {
                    Client c = clientMapper.selectById(inv.getClientId());
                    clientName = c == null ? "" : c.getClientName();
                }
                Row rw = paySheet.createRow(pr++);
                rw.createCell(0).setCellValue(String.valueOf(p.getPaymentDate()));
                rw.createCell(1).setCellValue(inv == null ? "" : nvlStr(inv.getInvoiceNo()));
                rw.createCell(2).setCellValue(clientName);
                rw.createCell(3).setCellValue(nvl(p.getAmount()).toPlainString());
                rw.createCell(4).setCellValue(exTax(p.getAmount(), inv, contract).toPlainString());
            }

            // ---------- 应收账龄 ----------
            Sheet aging = wb.createSheet("应收账龄");
            int ar = 0;
            Row ah = aging.createRow(ar++);
            ah.createCell(0).setCellValue("发票号");
            ah.createCell(1).setCellValue("客户");
            ah.createCell(2).setCellValue("开票日期");
            ah.createCell(3).setCellValue("账龄天数");
            ah.createCell(4).setCellValue("未回款（含税）");
            for (Invoice inv : invoiceMapper.selectList(null)) {
                if (inv.getStatus() == null || inv.getStatus() != 1) {
                    continue;
                }
                BigDecimal paid = BigDecimal.ZERO;
                for (ContractPayment p : paymentMapper.selectList(null)) {
                    if (inv.getId().equals(p.getInvoiceId()) && p.getAmount() != null) {
                        paid = paid.add(p.getAmount());
                    }
                }
                BigDecimal outstanding = nvl(inv.getAmount()).subtract(paid);
                if (outstanding.compareTo(new BigDecimal("0.005")) <= 0 || inv.getInvoiceDate() == null) {
                    continue;
                }
                long days = java.time.temporal.ChronoUnit.DAYS.between(inv.getInvoiceDate(), LocalDate.now());
                Row rw = aging.createRow(ar++);
                rw.createCell(0).setCellValue(nvlStr(inv.getInvoiceNo()));
                rw.createCell(1).setCellValue(inv.getClientId() == null ? "" : clientNameSafe(inv.getClientId()));
                rw.createCell(2).setCellValue(String.valueOf(inv.getInvoiceDate()));
                rw.createCell(3).setCellValue(days);
                rw.createCell(4).setCellValue(outstanding.toPlainString());
            }

            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private String clientNameSafe(Long clientId) {
        Client c = clientMapper.selectById(clientId);
        return c == null ? "" : c.getClientName();
    }

    private String nvlStr(String s) {
        return s == null ? "" : s;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 价税分离：优先发票税率，其次合同税率 */
    private BigDecimal exTax(BigDecimal amount, Invoice invoice, Contract contract) {
        BigDecimal amt = nvl(amount);
        BigDecimal rate = null;
        if (invoice != null && invoice.getTaxRate() != null) {
            rate = invoice.getTaxRate();
        } else if (contract != null && contract.getTaxRate() != null) {
            rate = contract.getTaxRate();
        }
        if (rate == null) {
            return amt;
        }
        return amt.divide(BigDecimal.ONE.add(rate.divide(new BigDecimal(100), 6, RoundingMode.HALF_UP)), 2, RoundingMode.HALF_UP);
    }

    private void row(Sheet sheet, int r, String... cells) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < cells.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(cells[i]);
        }
    }

    private <T> Map<Long, T> toMap(List<T> list, java.util.function.Function<T, Long> key) {
        Map<Long, T> map = new LinkedHashMap<>();
        for (T t : list) {
            map.put(key.apply(t), t);
        }
        return map;
    }
}
