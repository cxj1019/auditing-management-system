package com.accounting.firm.invoice.service;

import com.accounting.firm.common.exception.BusinessException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中国银行外汇牌价服务
 * <p>抓取 boc.cn 外汇牌价页（公开数据，无需密钥），按天缓存；
 * 牌价单位为「每 100 外币兑人民币」。</p>
 */
@Slf4j
@Service
public class ExchangeRateService {

    private static final String BOC_URL = "https://www.boc.cn/sourcedb/whpj/";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** 行数据：货币名称/现汇买入价/现钞买入价/现汇卖出价/现钞卖出价/中行折算价/发布时间 */
    @Data
    public static class RateRow {
        private String currencyName;
        private String spotBuy;
        private String cashBuy;
        private String spotSell;
        private String cashSell;
        private String bocRate;
        private String publishTime;
    }

    /** 当日缓存：日期 → 全部牌价行 */
    private final Map<LocalDate, List<RateRow>> cache = new ConcurrentHashMap<>();

    /**
     * 获取中国银行当前牌价（当日缓存）
     */
    public List<RateRow> currentRates() {
        LocalDate today = LocalDate.now();
        List<RateRow> cached = cache.get(today);
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = cache.get(today);
            if (cached != null) {
                return cached;
            }
            List<RateRow> rows = fetchFromBoc();
            cache.put(today, rows);
            // 清理过期缓存日期
            cache.keySet().removeIf(d -> !d.equals(today));
            return rows;
        }
    }

    /** 按货币名称取牌价行（如 美元/日元/欧元） */
    public RateRow findByName(String currencyName) {
        for (RateRow row : currentRates()) {
            if (row.getCurrencyName().equals(currencyName)) {
                return row;
            }
        }
        return null;
    }

    /** 抓取并解析牌价页 */
    private List<RateRow> fetchFromBoc() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BOC_URL))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String html = new String(response.body(), "UTF-8");

            List<RateRow> rows = new ArrayList<>();
            Matcher trMatcher = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL).matcher(html);
            while (trMatcher.find()) {
                List<String> tds = new ArrayList<>();
                Matcher tdMatcher = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL)
                        .matcher(trMatcher.group(1));
                while (tdMatcher.find()) {
                    tds.add(tdMatcher.group(1).replaceAll("<[^>]+>", "").trim());
                }
                if (tds.size() >= 7 && tds.get(6).matches("\\d{4}/\\d{2}/\\d{2}.*")) {
                    RateRow row = new RateRow();
                    row.setCurrencyName(tds.get(0));
                    row.setSpotBuy(tds.get(1));
                    row.setCashBuy(tds.get(2));
                    row.setSpotSell(tds.get(3));
                    row.setCashSell(tds.get(4));
                    row.setBocRate(tds.get(5));
                    row.setPublishTime(tds.get(6));
                    rows.add(row);
                }
            }
            if (rows.isEmpty()) {
                log.warn("[汇率] 中国银行牌价页解析为空");
                throw new BusinessException("获取中国银行牌价失败，请稍后重试或手动填写汇率");
            }
            log.info("[汇率] 中国银行牌价抓取成功，共 {} 个币种", rows.size());
            return rows;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[汇率] 中国银行牌价抓取失败: {}", e.getMessage());
            throw new BusinessException("获取中国银行牌价失败，请稍后重试或手动填写汇率");
        }
    }
}
