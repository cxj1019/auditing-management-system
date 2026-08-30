package com.accounting.firm.collection.mapper;

import com.accounting.firm.collection.dto.CollectionSummaryVO;
import com.accounting.firm.collection.dto.PaymentVO;
import com.accounting.firm.collection.entity.ContractPayment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 收款记录 Mapper（含关联合同的联表查询与汇总聚合）
 */
public interface ContractPaymentMapper extends BaseMapper<ContractPayment> {

    /** 分页查询收款记录（联表带出发票、合同与项目信息） */
    @Select("""
            <script>
            SELECT p.id, p.contract_id, p.invoice_id, p.amount, p.payment_date, p.payment_method, p.payer_name, p.remark, p.create_time,
                   inv.invoice_no,
                   c.contract_no, c.name AS contract_name,
                   cl.client_name, pr.project_no, pr.name AS project_name
            FROM contract_payment p
            JOIN contract c ON c.id = p.contract_id
            LEFT JOIN invoice inv ON inv.id = p.invoice_id
            LEFT JOIN project pr ON pr.id = c.project_id
            LEFT JOIN client cl ON cl.id = pr.client_id
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (c.contract_no LIKE '%' || #{keyword} || '%' OR c.name LIKE '%' || #{keyword} || '%'
                         OR inv.invoice_no LIKE '%' || #{keyword} || '%')
                </if>
                <if test="startDate != null">AND p.payment_date &gt;= #{startDate}</if>
                <if test="endDate != null">AND p.payment_date &lt;= #{endDate}</if>
                <if test="createByList != null and createByList.size() > 0">
                    AND c.create_by IN
                    <foreach item="item" collection="createByList" open="(" separator="," close=")">#{item}</foreach>
                </if>
            </where>
            ORDER BY p.payment_date DESC, p.id DESC
            </script>
            """)
    IPage<PaymentVO> selectPaymentPage(Page<?> page,
                                       @Param("keyword") String keyword,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("createByList") List<String> createByList);

    /** 按合同维度汇总收款（LEFT JOIN 保证零收款合同也出现） */
    @Select("""
            <script>
            SELECT c.id AS contract_id, c.contract_no, c.name AS contract_name,
                   cl.client_name,
                   c.amount AS contract_amount, COALESCE(SUM(p.amount), 0) AS total_collected
            FROM contract c
            LEFT JOIN project pr ON pr.id = c.project_id
            LEFT JOIN client cl ON cl.id = pr.client_id
            LEFT JOIN contract_payment p ON p.contract_id = c.id
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (c.contract_no LIKE '%' || #{keyword} || '%' OR c.name LIKE '%' || #{keyword} || '%'
                         OR cl.client_name LIKE '%' || #{keyword} || '%')
                </if>
            </where>
            GROUP BY c.id, c.contract_no, c.name, cl.client_name, c.amount, c.create_time
            ORDER BY c.create_time DESC
            </script>
            """)
    List<CollectionSummaryVO> selectSummary(@Param("keyword") String keyword);
}
