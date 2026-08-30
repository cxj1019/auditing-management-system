package com.accounting.firm.reimbursement;

import com.accounting.firm.reimbursement.entity.ReimbursementStatus;
import com.accounting.firm.reimbursement.service.ReimbursementNoGenerator;
import com.accounting.firm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报销状态机与编号生成单元测试
 */
class ReimbursementLogicTest {

    // ---------- 生命周期 ----------

    @Test
    void draftCanSubmit() {
        assertEquals(ReimbursementStatus.PENDING, ReimbursementStatus.DRAFT.submit());
    }

    @Test
    void pendingCanWithdraw() {
        assertEquals(ReimbursementStatus.DRAFT, ReimbursementStatus.PENDING.withdraw());
    }

    @Test
    void draftCannotWithdrawOrApprove() {
        assertThrows(BusinessException.class, () -> ReimbursementStatus.DRAFT.withdraw());
        assertThrows(BusinessException.class,
                () -> ReimbursementStatus.DRAFT.approveTo(ReimbursementStatus.APPROVED, false));
    }

    // ---------- 一级审批 ----------

    @Test
    void pendingFirstLevelCanApproveRejectOrTransfer() {
        assertEquals(ReimbursementStatus.APPROVED,
                ReimbursementStatus.PENDING.approveTo(ReimbursementStatus.APPROVED, false));
        assertEquals(ReimbursementStatus.REJECTED,
                ReimbursementStatus.PENDING.approveTo(ReimbursementStatus.REJECTED, false));
        assertEquals(ReimbursementStatus.PENDING_FINAL,
                ReimbursementStatus.PENDING.approveTo(ReimbursementStatus.PENDING_FINAL, false));
    }

    @Test
    void finalReviewOnlyFromPendingFinal() {
        assertEquals(ReimbursementStatus.APPROVED,
                ReimbursementStatus.PENDING_FINAL.approveTo(ReimbursementStatus.APPROVED, true));
        assertEquals(ReimbursementStatus.REJECTED,
                ReimbursementStatus.PENDING_FINAL.approveTo(ReimbursementStatus.REJECTED, true));
        assertThrows(BusinessException.class,
                () -> ReimbursementStatus.PENDING.approveTo(ReimbursementStatus.APPROVED, true));
    }

    @Test
    void finalStatesLocked() {
        assertTrue(ReimbursementStatus.APPROVED.isFinal());
        assertTrue(ReimbursementStatus.REJECTED.isFinal());
        assertThrows(BusinessException.class,
                () -> ReimbursementStatus.APPROVED.approveTo(ReimbursementStatus.REJECTED, true));
        assertThrows(BusinessException.class, () -> ReimbursementStatus.REJECTED.withdraw());
    }

    // ---------- 编号生成 ----------

    @Test
    void firstBillOfTheDay() {
        String no = ReimbursementNoGenerator.next(LocalDate.of(2026, 8, 22), null);
        assertEquals("BX202608220001", no);
    }

    @Test
    void sequenceIncrements() {
        String no = ReimbursementNoGenerator.next(LocalDate.of(2026, 8, 22), "BX202608220003");
        assertEquals("BX202608220004", no);
    }

    @Test
    void sequenceResetsOnNewDay() {
        String no = ReimbursementNoGenerator.next(LocalDate.of(2026, 8, 23), "BX202608220009");
        assertEquals("BX202608230001", no);
    }
}
