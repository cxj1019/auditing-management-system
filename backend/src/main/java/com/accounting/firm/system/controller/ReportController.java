package com.accounting.firm.system.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.report.MonthlyReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

/** 月度经营月报：下载 Excel / 发送到配置邮箱（仅管理员） */
@RestController
@RequestMapping("/api/system/report")
@PreAuthorize("hasAuthority('system:ai:list')")
public class ReportController {

    private final MonthlyReportService monthlyReportService;

    public ReportController(MonthlyReportService monthlyReportService) {
        this.monthlyReportService = monthlyReportService;
    }

    /** 下载指定月份月报 Excel */
    @GetMapping("/monthly.xlsx")
    public ResponseEntity<byte[]> download(@RequestParam String month) throws Exception {
        YearMonth ym = YearMonth.parse(month);
        byte[] xlsx = monthlyReportService.generateMonthly(ym);
        String fileName = URLEncoder.encode("经营月报_" + month + ".xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    /** 生成月报并发送到 report_mail_to */
    @PostMapping("/monthly/mail")
    public ApiResult<String> mail(@RequestParam String month) throws Exception {
        return ApiResult.success(monthlyReportService.mailMonthly(YearMonth.parse(month), "admin"));
    }
}
