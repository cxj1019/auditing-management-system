package com.accounting.firm.cost;

import com.accounting.firm.cost.dto.ProjectProfitVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 毛利率计算单元测试
 */
class MarginCalculatorTest {

    @Test
    void normalMargin() {
        BigDecimal margin = ProjectProfitVO.MarginCalculator.percent(new BigDecimal("30000"), new BigDecimal("100000"));
        assertEquals(new BigDecimal("30.00"), margin);
    }

    @Test
    void negativeMargin() {
        BigDecimal margin = ProjectProfitVO.MarginCalculator.percent(new BigDecimal("-20000"), new BigDecimal("50000"));
        assertEquals(new BigDecimal("-40.00"), margin);
    }

    @Test
    void zeroCollectedReturnsNull() {
        assertNull(ProjectProfitVO.MarginCalculator.percent(new BigDecimal("100"), BigDecimal.ZERO));
    }

    @Test
    void nullCollectedReturnsNull() {
        assertNull(ProjectProfitVO.MarginCalculator.percent(new BigDecimal("100"), null));
    }

    @Test
    void roundingToTwoDecimals() {
        // 1/3 → 33.333… → 33.33
        assertEquals(new BigDecimal("33.33"), ProjectProfitVO.MarginCalculator.percent(BigDecimal.ONE, new BigDecimal("3")));
    }
}
