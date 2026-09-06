package com.accounting.firm.reimbursement.mapper;

import com.accounting.firm.reimbursement.dto.ReimbursementExportVO;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 报销单 Mapper（含导出联表查询）
 */
public interface ReimbursementMapper extends BaseMapper<Reimbursement> {

    /** 导出费用明细扁平行（按明细费用日期范围筛选） */
    @Select("""
            <script>
            SELECT r.reimbursement_no, r.applicant_name, p.name AS project_name, r.title,
                   i.category AS item_category, i.amount AS item_amount,
                   i.expense_date AS item_expense_date, i.description AS item_description,
                   i.invoice_number, i.is_vat_invoice,
                   i.invoice_type, i.tax_rate,
                   COALESCE(i.project_id, r.project_id) AS item_project_id,
                   CASE r.status
                       WHEN 0 THEN '草稿' WHEN 1 THEN '待审批' WHEN 2 THEN '已批准'
                       WHEN 3 THEN '已驳回' WHEN 4 THEN '待终审' ELSE '未知' END AS status_label,
                   r.approver_name
            FROM reimbursement r
            JOIN reimbursement_item i ON i.reimbursement_id = r.id
            LEFT JOIN project p ON p.id = COALESCE(i.project_id, r.project_id)
            <where>
                <if test="startDate != null">AND i.expense_date &gt;= #{startDate}</if>
                <if test="endDate != null">AND i.expense_date &lt;= #{endDate}</if>
            </where>
            ORDER BY i.expense_date DESC, r.id DESC
            </script>
            """)
    List<ReimbursementExportVO> selectExportItems(@Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);
}
