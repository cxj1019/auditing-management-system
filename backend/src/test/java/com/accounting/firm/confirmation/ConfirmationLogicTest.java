package com.accounting.firm.confirmation;

import com.accounting.firm.confirmation.entity.ConfirmationStatus;
import com.accounting.firm.confirmation.service.ConfirmationNoGenerator;
import com.accounting.firm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 函证状态机与编号生成单元测试
 */
class ConfirmationLogicTest {

    // ---------- 状态机 ----------

    @Test
    void notSentCanSendOrVoid() {
        assertTrue(ConfirmationStatus.NOT_SENT.canTransitionTo(ConfirmationStatus.SENT));
        assertTrue(ConfirmationStatus.NOT_SENT.canTransitionTo(ConfirmationStatus.VOIDED));
        assertFalse(ConfirmationStatus.NOT_SENT.canTransitionTo(ConfirmationStatus.CONFIRMED));
    }

    @Test
    void sentCanConfirmOrVoid() {
        assertTrue(ConfirmationStatus.SENT.canTransitionTo(ConfirmationStatus.CONFIRMED));
        assertTrue(ConfirmationStatus.SENT.canTransitionTo(ConfirmationStatus.VOIDED));
        assertFalse(ConfirmationStatus.SENT.canTransitionTo(ConfirmationStatus.NOT_SENT));
    }

    @Test
    void confirmedAndVoidedAreFinal() {
        assertFalse(ConfirmationStatus.CONFIRMED.canTransitionTo(ConfirmationStatus.SENT));
        assertFalse(ConfirmationStatus.VOIDED.canTransitionTo(ConfirmationStatus.SENT));
        assertFalse(ConfirmationStatus.CONFIRMED.canTransitionTo(ConfirmationStatus.VOIDED));
    }

    @Test
    void illegalTransitionThrows() {
        assertThrows(BusinessException.class,
                () -> ConfirmationStatus.NOT_SENT.transitionTo(ConfirmationStatus.CONFIRMED));
        assertEquals(ConfirmationStatus.SENT,
                ConfirmationStatus.NOT_SENT.transitionTo(ConfirmationStatus.SENT));
    }

    // ---------- 编号生成 ----------

    @Test
    void firstNoOfTheDay() {
        assertEquals("HZ202608220001", ConfirmationNoGenerator.next(LocalDate.of(2026, 8, 22), null));
    }

    @Test
    void sequenceIncrements() {
        assertEquals("HZ202608220005", ConfirmationNoGenerator.next(LocalDate.of(2026, 8, 22), "HZ202608220004"));
    }

    @Test
    void sequenceResetsOnNewDay() {
        assertEquals("HZ202608230001", ConfirmationNoGenerator.next(LocalDate.of(2026, 8, 23), "HZ202608220009"));
    }
}
