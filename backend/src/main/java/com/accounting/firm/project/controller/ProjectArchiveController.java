package com.accounting.firm.project.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.confirmation.entity.ConfirmationAttachment;
import com.accounting.firm.confirmation.mapper.ConfirmationAttachmentMapper;
import com.accounting.firm.common.storage.SupabaseStorageService;
import com.accounting.firm.project.dto.ProjectBudgetItem;
import com.accounting.firm.project.dto.ProjectWorkbenchVO;
import com.accounting.firm.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 项目档案一键打包（CSV 清单 + 函证附件原文件） */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectArchiveController {

    private final ProjectService projectService;
    private final com.accounting.firm.confirmation.mapper.ConfirmationMapper confirmationMapper;
    private final ConfirmationAttachmentMapper confirmationAttachmentMapper;
    private final SupabaseStorageService storageService;

    @GetMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('business:project:list')")
    public ResponseEntity<byte[]> archive(@PathVariable Long id) throws Exception {
        com.accounting.firm.project.dto.ProjectWorkbenchVO wb = projectService.workbench(id);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        java.nio.charset.Charset cs = java.nio.charset.StandardCharsets.UTF_8;
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            // 00 项目信息
            StringBuilder info = new StringBuilder();
            info.append("项目编号,").append(wb.getProjectNo()).append('\n');
            info.append("项目名称,").append(wb.getProjectName()).append('\n');
            info.append("客户,").append(wb.getClientName() == null ? "" : wb.getClientName()).append('\n');
            info.append("状态,").append(wb.getStatusLabel()).append('\n');
            info.append("项目负责人,").append(wb.getManagerName() == null ? "" : wb.getManagerName()).append('\n');
            info.append("项目期间,").append(wb.getStartDate()).append(" ~ ").append(wb.getEndDate()).append('\n');
            info.append("预算工时,").append(wb.getBudgetHours()).append('\n');
            info.append("实际工时,").append(wb.getActualHours()).append('\n');
            zip.putNextEntry(new ZipEntry("00_项目信息.csv"));
            zip.write(info.toString().getBytes(cs));
            zip.closeEntry();

            // 01 汇总
            StringBuilder sum = new StringBuilder("指标,金额（元）\n");
            sum.append("合同总额,").append(wb.getContractAmount()).append('\n');
            sum.append("收入（不含税）,").append(wb.getTotalCollected()).append('\n');
            sum.append("直接成本（不含税）,").append(wb.getExpenseCost()).append('\n');
            sum.append("人工合计,").append(wb.getLaborCost()).append('\n');
            sum.append("毛利,").append(wb.getGrossProfit()).append('\n');
            zip.putNextEntry(new ZipEntry("01_汇总.csv"));
            zip.write(sum.toString().getBytes(cs));
            zip.closeEntry();

            // 02 预算工时
            StringBuilder budget = new StringBuilder("级别,人数,每人工时,预算工时,实际工时\n");
            for (Map<String, Object> l : wb.getBudgetLines()) {
                budget.append(l.get("levelName")).append(',').append(l.get("headcount")).append(',')
                        .append(l.get("hoursPerPerson")).append(',').append(l.get("totalHours")).append(',')
                        .append(l.get("actualHours")).append('\n');
            }
            zip.putNextEntry(new ZipEntry("02_预算工时.csv"));
            zip.write(budget.toString().getBytes(cs));
            zip.closeEntry();

            // 03 发票
            StringBuilder inv = new StringBuilder("发票号,类型,价税合计,不含税,税额,开票日期,已核销\n");
            for (var i : wb.getInvoices()) {
                inv.append(i.getInvoiceNo() == null ? "待开票" : i.getInvoiceNo()).append(',')
                        .append(i.getType()).append(',').append(i.getAmount()).append(',')
                        .append(i.getAmountExTax() == null ? "" : i.getAmountExTax()).append(',')
                        .append(i.getTaxAmount() == null ? "" : i.getTaxAmount()).append(',')
                        .append(i.getInvoiceDate() == null ? "" : i.getInvoiceDate()).append(",0\n");
            }
            zip.putNextEntry(new ZipEntry("03_发票.csv"));
            zip.write(inv.toString().getBytes(cs));
            zip.closeEntry();

            // 04 收款
            StringBuilder pay = new StringBuilder("日期,金额,不含税,方式\n");
            for (Map<String, Object> p : wb.getPayments()) {
                pay.append(p.get("paymentDate")).append(',').append(p.get("amount")).append(',')
                        .append(p.get("amountExTax")).append(',').append(p.get("paymentMethod")).append('\n');
            }
            zip.putNextEntry(new ZipEntry("04_收款.csv"));
            zip.write(pay.toString().getBytes(cs));
            zip.closeEntry();

            // 05 报销
            StringBuilder reimb = new StringBuilder("报销单,申请人,类别,不含税,日期,事由\n");
            for (Map<String, Object> r : wb.getReimbursements()) {
                reimb.append(r.get("reimbursementNo")).append(',').append(r.get("applicantName")).append(',')
                        .append(r.get("category")).append(',').append(r.get("amountExTax")).append(',')
                        .append(r.get("expenseDate")).append(",\"").append(r.get("description") == null ? "" : r.get("description")).append("\"\n");
            }
            zip.putNextEntry(new ZipEntry("05_报销.csv"));
            zip.write(reimb.toString().getBytes(cs));
            zip.closeEntry();

            // 06 对公付款
            StringBuilder vp = new StringBuilder("编号,供应商,摘要,不含税,日期\n");
            for (Map<String, Object> v : wb.getVendorPayments()) {
                vp.append(v.get("paymentNo")).append(',').append(v.get("vendorName")).append(",\"")
                        .append(v.get("summary") == null ? "" : v.get("summary")).append("\",")
                        .append(v.get("amountExTax")).append(',').append(v.get("paymentDate")).append('\n');
            }
            zip.putNextEntry(new ZipEntry("06_对公付款.csv"));
            zip.write(vp.toString().getBytes(cs));
            zip.closeEntry();

            // 07 函证清单 + 函证附件原文件
            StringBuilder cf = new StringBuilder("编号,被函证单位,类型,状态,发出日期,回函日期\n");
            int seq = 0;
            for (var c : wb.getConfirmations()) {
                cf.append(c.getConfirmationNo()).append(',').append(c.getTargetUnit()).append(',')
                        .append(c.getType()).append(',').append(c.getStatus()).append(',')
                        .append(c.getSentDate() == null ? "" : c.getSentDate()).append(',')
                        .append(c.getConfirmedDate() == null ? "" : c.getConfirmedDate()).append('\n');
                for (ConfirmationAttachment att : confirmationAttachmentMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConfirmationAttachment>()
                                .eq(ConfirmationAttachment::getConfirmationId, c.getId()))) {
                    try {
                        byte[] content = storageService.download(att.getStoredName());
                        seq++;
                        zip.putNextEntry(new ZipEntry("07_函证附件/" + seq + "_" + c.getConfirmationNo() + "_" + att.getFileName()));
                        zip.write(content);
                        zip.closeEntry();
                    } catch (Exception e) {
                        // 单个附件下载失败不阻断打包
                    }
                }
            }
            zip.putNextEntry(new ZipEntry("07_函证清单.csv"));
            zip.write(cf.toString().getBytes(cs));
            zip.closeEntry();
        }

        String fileName = java.net.URLEncoder.encode(
                "项目档案_" + wb.getProjectNo() + ".zip", StandardCharsets.UTF_8).replace("+", "%20");
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + fileName)
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/zip")
                .body(bos.toByteArray());
    }
}
