package com.accounting.firm.contract;

import com.accounting.firm.contract.entity.ContractStatus;
import com.accounting.firm.contract.service.ContractNoGenerator;
import com.accounting.firm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 合同状态机与编号生成单元测试
 */
class ContractLogicTest {

    // ---------- 状态机 ----------

    @Test
    void draftCanOnlyTransitionToRunning() {
        assertTrue(ContractStatus.DRAFT.canTransitionTo(ContractStatus.RUNNING));
        assertFalse(ContractStatus.DRAFT.canTransitionTo(ContractStatus.FINISHED));
        assertFalse(ContractStatus.DRAFT.canTransitionTo(ContractStatus.TERMINATED));
    }

    @Test
    void runningCanFinishOrTerminate() {
        assertTrue(ContractStatus.RUNNING.canTransitionTo(ContractStatus.FINISHED));
        assertTrue(ContractStatus.RUNNING.canTransitionTo(ContractStatus.TERMINATED));
        assertFalse(ContractStatus.RUNNING.canTransitionTo(ContractStatus.DRAFT));
    }

    @Test
    void finishedAndTerminatedAreFinal() {
        assertFalse(ContractStatus.FINISHED.canTransitionTo(ContractStatus.RUNNING));
        assertFalse(ContractStatus.TERMINATED.canTransitionTo(ContractStatus.RUNNING));
        assertFalse(ContractStatus.FINISHED.canTransitionTo(ContractStatus.TERMINATED));
    }

    @Test
    void illegalTransitionThrowsBusinessException() {
        assertThrows(BusinessException.class,
                () -> ContractStatus.FINISHED.transitionTo(ContractStatus.RUNNING));
        assertEquals(ContractStatus.RUNNING, ContractStatus.DRAFT.transitionTo(ContractStatus.RUNNING));
    }

    // ---------- 编号生成 ----------

    @Test
    void firstContractOfTheDayEndsWith0001() {
        String no = ContractNoGenerator.next(LocalDate.of(2026, 8, 21), null);
        assertEquals("HT202608210001", no);
    }

    @Test
    void sequenceIncrementsFromMaxSameDayNo() {
        String no = ContractNoGenerator.next(LocalDate.of(2026, 8, 21), "HT202608210007");
        assertEquals("HT202608210008", no);
    }

    @Test
    void sequenceResetsOnNewDay() {
        String no = ContractNoGenerator.next(LocalDate.of(2026, 8, 22), "HT202608210009");
        assertEquals("HT202608220001", no);
    }

    @Test
    void ignoresMaxNoFromDifferentDay() {
        String no = ContractNoGenerator.next(LocalDate.of(2026, 9, 1), "HT208812319999");
        assertEquals("HT202609010001", no);
    }

    // ---------- 字号规则（按类型+年份编号） ----------

    @Test
    void firstStructuredNoStartsFrom0001() {
        String no = ContractNoGenerator.nextStructured("迈伊兹审约", 2026, null);
        assertEquals("迈伊兹审约(2026)第0001号", no);
    }

    @Test
    void structuredNoIncrementsWithinSameTypeAndYear() {
        String no = ContractNoGenerator.nextStructured("迈伊兹审验", 2026, "迈伊兹审验(2026)第0007号");
        assertEquals("迈伊兹审验(2026)第0008号", no);
    }

    @Test
    void structuredNoResetsOnNewYear() {
        String no = ContractNoGenerator.nextStructured("迈伊兹审约", 2027, "迈伊兹审约(2026)第0009号");
        assertEquals("迈伊兹审约(2027)第0001号", no);
    }

    @Test
    void structuredNoIgnoresMaxOfOtherTypeOrYear() {
        String no = ContractNoGenerator.nextStructured("迈伊兹审约", 2026, "迈伊兹审验(2026)第0005号");
        assertEquals("迈伊兹审约(2026)第0001号", no);
        String no2 = ContractNoGenerator.nextStructured("咨", 2026, "咨(2025)第0005号");
        assertEquals("咨(2026)第0001号", no2);
    }
}
