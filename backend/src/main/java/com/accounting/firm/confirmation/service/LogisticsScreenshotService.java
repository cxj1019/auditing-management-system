package com.accounting.firm.confirmation.service;

import com.accounting.firm.common.exception.BusinessException;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 物流截图服务：使用 Playwright 打开快递官网查询页面并截图
 * <p>根据快递单号自动识别快递公司，优先使用官网查询（避免第三方平台隐私限制）。</p>
 */
@Slf4j
@Service
public class LogisticsScreenshotService {

    private static volatile Playwright playwright;
    private static volatile Browser browser;
    private static final ReentrantLock initLock = new ReentrantLock();

    /**
     * 截图物流查询页面
     *
     * @param trackingNo 快递单号
     * @return 截图 PNG 字节数组
     */
    public byte[] screenshotLogistics(String trackingNo) {
        ensureBrowser();
        String courier = detectCourier(trackingNo);
        log.info("物流查询: trackingNo={}, detectedCourier={}", trackingNo, courier);

        try (BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(1280, 800)
                        .setLocale("zh-CN")
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        )) {
            Page page = context.newPage();

            switch (courier) {
                case "zto" -> screenshotZTO(page, trackingNo);
                case "sf" -> screenshotSF(page, trackingNo);
                case "jt" -> screenshotJT(page, trackingNo);
                default -> screenshotKuaidi100(page, trackingNo);
            }

            // 全页截图：捕获完整物流信息（包括需要滚动才能看到的部分）
            byte[] screenshot = page.screenshot(
                    new Page.ScreenshotOptions().setFullPage(true));
            log.info("物流截图成功: trackingNo={}, courier={}, size={} bytes", trackingNo, courier, screenshot.length);
            return screenshot;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("物流截图失败: trackingNo={}", trackingNo, e);
            throw new BusinessException("物流截图失败，请稍后重试");
        }
    }

    /** 根据单号规则识别快递公司 */
    String detectCourier(String trackingNo) {
        String no = trackingNo.trim().toUpperCase();
        // 顺丰：SF + 12-15 位数字
        if (no.startsWith("SF") && no.length() >= 14) return "sf";
        // 极兔：JT + 13 位（官网无法直接查询，使用快递100）
        if (no.startsWith("JT")) return "kuaidi100";
        // 中通：12-13 位纯数字，通常以 7 开头
        if (no.matches("\\d{12,13}") && no.startsWith("7")) return "zto";
        // 圆通：YT + 13 位
        if (no.startsWith("YT")) return "yto";
        // 申通：773 开头
        if (no.startsWith("773")) return "sto";
        // 韵达：YD 开头
        if (no.startsWith("YD")) return "yunda";
        // 默认使用快递100（通用查询）
        return "kuaidi100";
    }

    /** 中通官网查询（需表单交互） */
    private void screenshotZTO(Page page, String trackingNo) {
        page.navigate("https://www.zto.com/generateOrderQuery.html",
                new Page.NavigateOptions().setTimeout(30000));
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(2000);

        // 尝试多种选择器找到输入框
        String[] inputSelectors = {
                "input[placeholder*='运单']",
                "input[placeholder*='单号']",
                "input[type='text']",
                ".input-box input",
                "#inputBox",
        };
        boolean filled = false;
        for (String selector : inputSelectors) {
            try {
                var input = page.querySelector(selector);
                if (input != null && input.isVisible()) {
                    input.fill(trackingNo);
                    filled = true;
                    break;
                }
            } catch (Exception ignored) {}
        }
        if (!filled) {
            page.keyboard().type(trackingNo);
        }

        page.waitForTimeout(500);

        String[] buttonSelectors = {
                "button:has-text('查询')",
                ".btn-search",
                "input[type='submit']",
                ".search-btn",
        };
        boolean clicked = false;
        for (String selector : buttonSelectors) {
            try {
                var btn = page.querySelector(selector);
                if (btn != null && btn.isVisible()) {
                    btn.click();
                    clicked = true;
                    break;
                }
            } catch (Exception ignored) {}
        }
        if (!clicked) {
            page.keyboard().press("Enter");
        }

        page.waitForTimeout(5000);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE);
        } catch (Exception ignored) {}
        page.waitForTimeout(2000);
    }

    /** 极兔官网查询 */
    private void screenshotJT(Page page, String trackingNo) {
        page.navigate("https://www.jtexpress.com.cn/index.html",
                new Page.NavigateOptions().setTimeout(30000));
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(2000);

        String[] inputSelectors = {
                "input[placeholder*='单号']",
                "input[placeholder*='运单']",
                "input[type='text']",
        };
        boolean filled = false;
        for (String selector : inputSelectors) {
            try {
                var input = page.querySelector(selector);
                if (input != null && input.isVisible()) {
                    input.fill(trackingNo);
                    filled = true;
                    break;
                }
            } catch (Exception ignored) {}
        }
        if (!filled) {
            page.keyboard().type(trackingNo);
        }
        page.waitForTimeout(500);
        page.keyboard().press("Enter");
        page.waitForTimeout(5000);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE);
        } catch (Exception ignored) {}
        page.waitForTimeout(2000);
    }

    /** 顺丰官网查询 */
    private void screenshotSF(Page page, String trackingNo) {
        page.navigate("https://www.sf-express.com/chn/sc/waybill/waybill-detail/" + trackingNo,
                new Page.NavigateOptions().setTimeout(30000));
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(3000);
    }

    /** 快递100 通用查询 */
    private void screenshotKuaidi100(Page page, String trackingNo) {
        page.navigate("https://www.kuaidi100.com/chaxun?nu=" + trackingNo,
                new Page.NavigateOptions().setTimeout(30000));
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(5000);
        try {
            page.waitForSelector(".query-result", new Page.WaitForSelectorOptions().setTimeout(8000));
        } catch (Exception ignored) {}
    }

    /** 懒初始化浏览器 */
    private synchronized void ensureBrowser() {
        if (browser != null && browser.isConnected()) {
            return;
        }
        initLock.lock();
        try {
            if (browser == null || !browser.isConnected()) {
                log.info("初始化 Playwright 浏览器...");
                playwright = Playwright.create();
                browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions().setHeadless(true));
                log.info("Playwright 浏览器初始化完成");
            }
        } catch (Exception e) {
            log.error("Playwright 浏览器初始化失败", e);
            throw new BusinessException("浏览器初始化失败，请检查网络连接");
        } finally {
            initLock.unlock();
        }
    }
}
