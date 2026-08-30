package com.accounting.firm.invoice.service;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.invoice.dto.InvoiceOptionVO;
import com.accounting.firm.invoice.dto.InvoiceRequest;
import com.accounting.firm.invoice.dto.InvoiceSummaryVO;
import com.accounting.firm.invoice.dto.InvoiceVO;
import com.accounting.firm.invoice.entity.Invoice;
import com.accounting.firm.invoice.entity.InvoiceAttachment;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 发票服务
 */
public interface InvoiceService extends IService<Invoice> {

    /** 分页筛选查询发票（联表合同/项目/客户与核销金额） */
    PageResult<InvoiceVO> pageInvoices(long current, long size, String keyword,
                                       String type, Integer status);

    /** 登记发票（挂靠非草稿合同，客户由项目自动带出） */
    void createInvoice(InvoiceRequest request);

    /** 编辑发票（不可变更所属合同；已作废不可编辑） */
    void updateInvoice(InvoiceRequest request);

    /** 删除发票（仅待开票且无收款核销） */
    void deleteInvoice(Long id);

    /** 状态流转：issue 开票 / void 作废 */
    void changeStatus(Long id, String action, LocalDate invoiceDate);

    /** 已开票发票下拉选项（供收款核销选择） */
    List<InvoiceOptionVO> options(String keyword);

    /** 按发票维度核销汇总（发票金额 vs 已收核销，排除已作废） */
    List<InvoiceSummaryVO> summary(String keyword);

    /** 上传发票扫描件 */
    InvoiceAttachment uploadAttachment(Long invoiceId, MultipartFile file);

    /** 查询发票附件 */
    List<InvoiceAttachment> listAttachments(Long invoiceId);

    /** 按 ID 取附件（校验归属） */
    InvoiceAttachment getAttachment(Long invoiceId, Long attachmentId);

    /** 下载附件内容 */
    byte[] downloadAttachment(InvoiceAttachment attachment);

    /** 删除附件 */
    void deleteAttachment(Long invoiceId, Long attachmentId);
}
