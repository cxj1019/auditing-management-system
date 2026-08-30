package com.accounting.firm.project.service;

import java.time.LocalDate;

/**
 * 项目编号生成器
 * <p>规则：PRJ + yyyyMMdd + 4 位当日流水号（如 PRJ202608220001）。纯函数设计。</p>
 */
public final class ProjectNoGenerator {

    private static final String PREFIX = "PRJ";
    private static final int SEQ_WIDTH = 4;

    private ProjectNoGenerator() {
    }

    /**
     * 生成下一个项目编号
     *
     * @param date          当前日期
     * @param maxExistingNo 当日已存在的最大编号（可为 null）
     * @return 新的项目编号
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
}
