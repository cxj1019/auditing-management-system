package com.accounting.firm.cost.mapper;

import com.accounting.firm.cost.dto.ProjectProfitVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 成本分析聚合 Mapper（按项目维度）
 */
public interface CostAnalysisMapper {

    /** 按项目维度聚合收入/成本（关键字筛选项目编号/名称/客户） */
    @Select("""
            <script>
            SELECT p.id AS project_id, p.project_no, p.name AS project_name, cl.client_name,
                   COALESCE(amt.contract_amount, 0) AS contract_amount,
                   COALESCE(rev.income, 0) AS total_collected,
                   COALESCE(exp.expense, 0) AS expense_cost,
                   COALESCE(l.labor, 0) AS labor_cost
            FROM project p
            LEFT JOIN client cl ON cl.id = p.client_id
            LEFT JOIN (SELECT project_id, SUM(amount) AS contract_amount
                       FROM contract GROUP BY project_id) amt ON amt.project_id = p.id
            LEFT JOIN (SELECT c.project_id, SUM(
                            CASE WHEN COALESCE(inv.tax_rate, c.tax_rate) IS NOT NULL
                                 THEN cp.amount / (1 + COALESCE(inv.tax_rate, c.tax_rate) / 100)
                                 ELSE cp.amount END) AS income
                       FROM contract_payment cp
                       JOIN contract c ON c.id = cp.contract_id
                       LEFT JOIN invoice inv ON inv.id = cp.invoice_id
                       WHERE c.project_id IS NOT NULL
                       GROUP BY c.project_id) rev ON rev.project_id = p.id
            LEFT JOIN (SELECT COALESCE(i.project_id, r.project_id) AS project_id,
                              SUM(CASE WHEN i.invoice_type = 'vat_special' AND i.tax_rate IS NOT NULL
                                       THEN i.amount / (1 + i.tax_rate / 100)
                                       ELSE i.amount END) AS expense
                       FROM reimbursement_item i
                       JOIN reimbursement r ON r.id = i.reimbursement_id
                       WHERE r.status = 1 AND COALESCE(i.project_id, r.project_id) IS NOT NULL
                       GROUP BY COALESCE(i.project_id, r.project_id)) exp ON exp.project_id = p.id
            LEFT JOIN (SELECT project_id, SUM(amount) AS labor
                       FROM labor_cost GROUP BY project_id) l ON l.project_id = p.id
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (p.project_no LIKE '%' || #{keyword} || '%' OR p.name LIKE '%' || #{keyword} || '%'
                         OR cl.client_name LIKE '%' || #{keyword} || '%')
                </if>
                <if test="deptId != null">AND p.dept_id = #{deptId}</if>
                <if test="ownUsername != null">AND p.create_by = #{ownUsername}</if>
                <if test="year != null">AND EXTRACT(YEAR FROM p.start_date) = #{year}</if>
            </where>
            ORDER BY p.create_time DESC
            </script>
            """)
    List<ProjectProfitVO> selectProjectProfit(@Param("keyword") String keyword,
                                              @Param("deptId") Long deptId,
                                              @Param("ownUsername") String ownUsername,
                                              @Param("year") Integer year);
}
