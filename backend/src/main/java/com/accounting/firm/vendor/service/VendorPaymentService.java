package com.accounting.firm.vendor.service;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.vendor.entity.VendorPayment;
import com.accounting.firm.vendor.entity.VendorPaymentAttachment;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 对公付款服务：登记 → 审批 → 付款，可归集项目计入成本
 */
public interface VendorPaymentService extends IService<VendorPayment> {

    PageResult<VendorPayment> page(long current, long size, Integer status, String keyword, SecurityUser currentUser);

    Long create(VendorPayment request, SecurityUser currentUser);

    void update(VendorPayment request, SecurityUser currentUser);

    void submit(Long id, SecurityUser currentUser);

    void withdraw(Long id, SecurityUser currentUser);

    void delete(Long id, SecurityUser currentUser);

    void approve(Long id, String action, String comment, SecurityUser currentUser);

    void markPaid(Long id, SecurityUser currentUser);

    List<VendorPaymentAttachment> listAttachments(Long paymentId);

    VendorPaymentAttachment uploadAttachment(Long paymentId, MultipartFile file, SecurityUser currentUser);

    void deleteAttachment(Long paymentId, Long attachmentId, SecurityUser currentUser);

    byte[] downloadAttachment(Long attachmentId);
}
