package com.accounting.firm.contract.service;

import java.time.LocalDate;

/**
 * 合同编号生成器
 * <p>两种规则，纯函数设计便于单元测试；并发冲突由数据库唯一约束兜底：</p>
 * <ul>
 *   <li>字号规则（配置了字号类型的合同）：{前缀}({年份})第{4 位流水}号，
 *       流水按「类型 + 年份」独立递增，如 迈伊兹审约(2026)第0001号</li>
 *   <li>兜底规则（未配置字号类型的合同）：HT + yyyyMMdd + 4 位当日流水号</li>
 * </ul>
 */
public final class ContractNoGenerator {

    private static final String PREFIX = "HT";
    private static final int SEQ_WIDTH = 4;
    /** 字号规则流水位数：第0001号 */
    private static final int STRUCTURED_SEQ_WIDTH = 4;
    private static final String STRUCTURED_SUFFIX = "号";

    private ContractNoGenerator() {
    }

    /**
     * 生成下一个合同编号（兜底规则）
     *
     * @param date          当前日期
     * @param maxExistingNo 当日已存在的最大编号（可为 null）
     * @return 新的合同编号
     */
    public static String next(LocalDate date, String maxExistingNo) {
        String prefix = PREFIX + "%1$tY%1$tm%1$td".formatted(date);
        int sequence = 1;
        if (maxExistingNo != null && maxExistingNo.startsWith(prefix)
                && maxExistingNo.length() == prefix.length() + SEQ_WIDTH) {
            sequence = Integer.parseInt(maxExistingNo.substring(prefix.length())) + 1;
        }
        return prefix + ("%0" + SEQ_WIDTH + "d").formatted(sequence);
    }

    /**
     * 生成下一个合同字号（字号规则）
     *
     * @param prefix        字号前缀，如 迈伊兹审约
     * @param year          合同年份（取签约日期年份）
     * @param maxExistingNo 同类型同年份已存在的最大字号（可为 null）
     * @return 新的合同字号，如 迈伊兹审约(2026)第0001号
     */
    public static String nextStructured(String prefix, int year, String maxExistingNo) {
        String head = prefix + "(" + year + ")第";
        int sequence = 1;
        if (maxExistingNo != null && maxExistingNo.startsWith(head)
                && maxExistingNo.endsWith(STRUCTURED_SUFFIX)) {
            String digits = maxExistingNo.substring(head.length(),
                    maxExistingNo.length() - STRUCTURED_SUFFIX.length());
            if (digits.length() >= STRUCTURED_SEQ_WIDTH) {
                sequence = Integer.parseInt(
                        digits.substring(digits.length() - STRUCTURED_SEQ_WIDTH)) + 1;
            }
        }
        return head + ("%0" + STRUCTURED_SEQ_WIDTH + "d").formatted(sequence) + STRUCTURED_SUFFIX;
    }
}
