package com.accounting.firm.invoice.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.invoice.service.ExchangeRateService;
import com.accounting.firm.invoice.service.ExchangeRateService.RateRow;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 中国银行外汇牌价接口
 */
@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    /** 历史人民币汇率中间价（中国外汇交易中心，按日期查询；当日请用实时牌价） */
    @GetMapping("/history")
    public ApiResult<List<java.util.Map<String, String>>> history(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        return ApiResult.success(exchangeRateService.historyParity(date));
    }

    /** 当前牌价（每 100 外币兑人民币；当日缓存）。可按货币名称过滤，如 美元/日元/欧元 */
    @GetMapping
    public ApiResult<List<RateRow>> list(@RequestParam(required = false) String currencyName) {
        List<RateRow> rows = exchangeRateService.currentRates();
        if (currencyName != null && !currencyName.isEmpty()) {
            rows = rows.stream().filter(r -> r.getCurrencyName().equals(currencyName)).toList();
        }
        return ApiResult.success(rows);
    }
}
