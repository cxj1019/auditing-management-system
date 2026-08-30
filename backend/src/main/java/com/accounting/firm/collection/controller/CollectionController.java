package com.accounting.firm.collection.controller;

import com.accounting.firm.collection.dto.CollectionSummaryVO;
import com.accounting.firm.collection.dto.PaymentRequest;
import com.accounting.firm.collection.dto.PaymentVO;
import com.accounting.firm.collection.service.CollectionService;
import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 收款管理接口
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;
    private final com.accounting.firm.invoice.service.InvoiceService invoiceService;

    /** 分页筛选查询收款记录 */
    @PreAuthorize("hasAuthority('business:collection:list')")
    @GetMapping
    public ApiResult<PageResult<PaymentVO>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResult.success(collectionService.pagePayments(current, size, keyword, startDate, endDate));
    }

    /** 按合同维度汇总收款 */
    @PreAuthorize("hasAuthority('business:collection:list')")
    @GetMapping("/summary")
    public ApiResult<List<CollectionSummaryVO>> summary(@RequestParam(required = false) String keyword) {
        return ApiResult.success(collectionService.summary(keyword));
    }

    /** 按发票维度核销汇总（发票金额 vs 已收核销） */
    @PreAuthorize("hasAnyAuthority('business:collection:list', 'business:invoice:list')")
    @GetMapping("/invoice-summary")
    public ApiResult<List<com.accounting.firm.invoice.dto.InvoiceSummaryVO>> invoiceSummary(
            @RequestParam(required = false) String keyword) {
        return ApiResult.success(invoiceService.summary(keyword));
    }

    /** 登记收款 */
    @AuditLog("登记收款")
    @PreAuthorize("hasAuthority('business:collection:add')")
    @PostMapping
    public ApiResult<Void> add(@Valid @RequestBody PaymentRequest request) {
        collectionService.addPayment(request);
        return ApiResult.success();
    }

    /** 编辑收款（不可变更所属发票/合同） */
    @AuditLog("编辑收款")
    @PreAuthorize("hasAuthority('business:collection:edit')")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        collectionService.updatePayment(id, request);
        return ApiResult.success();
    }

    /** 预收核销：将未核销收款关联到同一合同的已开票发票 */
    @AuditLog("预收核销")
    @PreAuthorize("hasAuthority('business:collection:edit')")
    @PutMapping("/{id}/write-off")
    public ApiResult<Void> writeOff(@PathVariable Long id, @RequestParam Long invoiceId) {
        collectionService.writeOff(id, invoiceId);
        return ApiResult.success();
    }

    /** 删除收款 */
    @AuditLog("删除收款")
    @PreAuthorize("hasAuthority('business:collection:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        collectionService.deletePayment(id);
        return ApiResult.success();
    }
}
