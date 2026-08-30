package com.accounting.firm.invoice.mapper;

import com.accounting.firm.invoice.dto.InvoiceSummaryVO;
import com.accounting.firm.invoice.dto.InvoiceVO;
import com.accounting.firm.invoice.entity.Invoice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 发票 Mapper（联表带出合同/项目/客户与核销汇总）
 */
public interface InvoiceMapper extends BaseMapper<Invoice> {

    /** 分页查询发票（联表合同/项目/客户，并聚合每张发票的已收金额） */
    @Select("""
            <script>
            SELECT i.id, i.invoice_no, i.contract_id, i.client_id, i.type, i.amount, i.tax_rate,
                   i.amount_ex_tax, i.tax_amount, i.currency, i.foreign_amount, i.exchange_rate, i.rate_publish_time,
                   i.invoice_item, i.tax_code, i.tax_class,
                   i.invoice_date, i.status, i.remark, i.create_time,
                   c.contract_no, c.name AS contract_name,
                   p.project_no, p.name AS project_name,
                   cl.client_name,
                   COALESCE(pay.collected, 0) AS collected_amount
            FROM invoice i
            JOIN contract c ON c.id = i.contract_id
            LEFT JOIN project p ON p.id = c.project_id
            LEFT JOIN client cl ON cl.id = i.client_id
            LEFT JOIN (
                SELECT invoice_id, SUM(amount) AS collected
                FROM contract_payment WHERE invoice_id IS NOT NULL GROUP BY invoice_id
            ) pay ON pay.invoice_id = i.id
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (i.invoice_no LIKE '%' || #{keyword} || '%'
                         OR c.contract_no LIKE '%' || #{keyword} || '%'
                         OR c.name LIKE '%' || #{keyword} || '%'
                         OR cl.client_name LIKE '%' || #{keyword} || '%')
                </if>
                <if test="type != null and type != ''">AND i.type = #{type}</if>
                <if test="status != null">AND i.status = #{status}</if>
                <if test="createByList != null and createByList.size() > 0">
                    AND i.create_by IN
                    <foreach item="item" collection="createByList" open="(" separator="," close=")">#{item}</foreach>
                </if>
            </where>
            ORDER BY i.create_time DESC, i.id DESC
            </script>
            """)
    IPage<InvoiceVO> selectInvoicePage(Page<?> page,
                                       @Param("keyword") String keyword,
                                       @Param("type") String type,
                                       @Param("status") Integer status,
                                       @Param("createByList") List<String> createByList);

    /** 按发票维度核销汇总（发票金额 vs 已收核销） */
    @Select("""
            <script>
            SELECT i.id AS invoice_id, i.invoice_no, i.type,
                   c.contract_no, c.name AS contract_name,
                   cl.client_name,
                   i.amount AS invoice_amount, COALESCE(SUM(p.amount), 0) AS collected_amount
            FROM invoice i
            JOIN contract c ON c.id = i.contract_id
            LEFT JOIN client cl ON cl.id = i.client_id
            LEFT JOIN contract_payment p ON p.invoice_id = i.id
            <where>
                i.status != 2
                <if test="keyword != null and keyword != ''">
                    AND (i.invoice_no LIKE '%' || #{keyword} || '%'
                         OR c.contract_no LIKE '%' || #{keyword} || '%'
                         OR c.name LIKE '%' || #{keyword} || '%'
                         OR cl.client_name LIKE '%' || #{keyword} || '%')
                </if>
                <if test="createByList != null and createByList.size() > 0">
                    AND i.create_by IN
                    <foreach item="item" collection="createByList" open="(" separator="," close=")">#{item}</foreach>
                </if>
            </where>
            GROUP BY i.id, i.invoice_no, i.type, c.contract_no, c.name, cl.client_name, i.amount, i.create_time
            ORDER BY i.create_time DESC, i.id DESC
            </script>
            """)
    List<InvoiceSummaryVO> selectInvoiceSummary(@Param("keyword") String keyword,
                                                @Param("createByList") List<String> createByList);
}
