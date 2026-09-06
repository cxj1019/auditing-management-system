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
                <if test="deptId != null">
                    AND c.project_id IN (SELECT id FROM project WHERE dept_id = #{deptId})
                </if>
                <if test="selfCreateBy != null">AND c.create_by = #{selfCreateBy}</if>
            </where>
            ORDER BY p.payment_date DESC, p.id DESC
            </script>
            """)
    IPage<PaymentVO> selectPaymentPage(Page<?> page,
                                       @Param("keyword") String keyword,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("deptId") Long deptId,
                                       @Param("selfCreateBy") String selfCreateBy);

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
                <if test="deptId != null">
                    AND c.project_id IN (SELECT id FROM project WHERE dept_id = #{deptId})
                </if>
                <if test="selfCreateBy != null">AND c.create_by = #{selfCreateBy}</if>
            </where>
            GROUP BY c.id, c.contract_no, c.name, cl.client_name, c.amount, c.create_time
            ORDER BY c.create_time DESC
            </script>
            """)
    List<CollectionSummaryVO> selectSummary(@Param("keyword") String keyword,
                                            @Param("deptId") Long deptId,
                                            @Param("selfCreateBy") String selfCreateBy);

    /**
     * 垫付台账（按项目归集）：垫付报销合计 / 垫付开票合计 / 垫付回款合计
     * 仅返回有垫付活动（报销或垫付开票）的项目
     */
    @Select("""
            <script>
            SELECT p.id AS project_id, p.project_no, p.name AS project_name, cl.client_name,
                   COALESCE(rch.recharge_total, 0) AS recharge_total,
                   COALESCE(inv.invoiced_total, 0) AS invoiced_total,
                   COALESCE(pay.collected_total, 0) AS collected_total
            FROM project p
            LEFT JOIN client cl ON cl.id = p.client_id
            LEFT JOIN (
                SELECT COALESCE(i.project_id, r.project_id) AS project_id, SUM(i.amount) AS recharge_total
                FROM reimbursement_item i
                JOIN reimbursement r ON r.id = i.reimbursement_id
                WHERE r.status = 1 AND i.billable = TRUE
                GROUP BY COALESCE(i.project_id, r.project_id)
            ) rch ON rch.project_id = p.id
            LEFT JOIN (
                SELECT cc.project_id, SUM(ii.amount) AS invoiced_total
                FROM invoice ii
                JOIN contract cc ON cc.id = ii.contract_id
                WHERE ii.is_recharge = TRUE AND ii.status = 1
                GROUP BY cc.project_id
            ) inv ON inv.project_id = p.id
            LEFT JOIN (
                SELECT cc.project_id, SUM(cp.amount) AS collected_total
                FROM contract_payment cp
                JOIN invoice ii ON ii.id = cp.invoice_id
                JOIN contract cc ON cc.id = cp.contract_id
                WHERE ii.is_recharge = TRUE
                GROUP BY cc.project_id
            ) pay ON pay.project_id = p.id
            <where>
                p.id IN (
                    SELECT COALESCE(i.project_id, r.project_id) FROM reimbursement_item i
                    JOIN reimbursement r ON r.id = i.reimbursement_id
                    WHERE r.status = 1 AND i.billable = TRUE
                    UNION
                    SELECT cc.project_id FROM invoice ii JOIN contract cc ON cc.id = ii.contract_id
                    WHERE ii.is_recharge = TRUE AND ii.status = 1
                )
                <if test="deptId != null">AND p.dept_id = #{deptId}</if>
                <if test="selfCreateBy != null">AND p.create_by = #{selfCreateBy}</if>
            </where>
            ORDER BY p.create_time DESC
            </script>
            """)
    List<com.accounting.firm.collection.dto.RechargeLedgerVO> selectRechargeLedger(
            @Param("deptId") Long deptId,
            @Param("selfCreateBy") String selfCreateBy);
}
