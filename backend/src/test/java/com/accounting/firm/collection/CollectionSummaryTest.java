package com.accounting.firm.collection;

import com.accounting.firm.collection.dto.CollectionSummaryVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 收款汇总进度计算单元测试
 */
class CollectionSummaryTest {

    @Test
    void normalProgress() {
        assertEquals(50, CollectionSummaryVO.SummaryCalculator.percent(new BigDecimal("50000"), new BigDecimal("100000")));
    }

    @Test
    void fullProgress() {
        assertEquals(100, CollectionSummaryVO.SummaryCalculator.percent(new BigDecimal("100000.00"), new BigDecimal("100000.00")));
    }

    @Test
    void overCollectedExceedsHundred() {
        assertEquals(120, CollectionSummaryVO.SummaryCalculator.percent(new BigDecimal("120000"), new BigDecimal("100000")));
    }

    @Test
    void zeroContractAmountAvoidsDivideByZero() {
        assertEquals(0, CollectionSummaryVO.SummaryCalculator.percent(new BigDecimal("100"), BigDecimal.ZERO));
    }

    @Test
    void nullContractAmountTreatedAsZero() {
        assertEquals(0, CollectionSummaryVO.SummaryCalculator.percent(new BigDecimal("100"), null));
    }

    @Test
    void roundingHalfUp() {
        // 1/3 → 33.33… → 33
        assertEquals(33, CollectionSummaryVO.SummaryCalculator.percent(BigDecimal.ONE, new BigDecimal("3")));
    }
}
