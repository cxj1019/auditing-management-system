package com.accounting.firm.reimbursement.mapper;

import com.accounting.firm.reimbursement.dto.ReimbursementExportVO;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * 报销单 Mapper（含导出联表查询）
 */
public interface ReimbursementMapper extends BaseMapper<Reimbursement> {


    /** 回收站：已软删除的报销单（绕过 @TableLogic） */
    @Select("""
            SELECT * FROM reimbursement WHERE deleted = 1
            ORDER BY update_time DESC
            """)
    List<Reimbursement> selectDeleted();

    @Update("UPDATE reimbursement SET deleted = 0 WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    /** 回收站超过保留期的单据（供彻底清除） */
    @Select("""
            SELECT * FROM reimbursement
            WHERE deleted = 1 AND update_time < #{cutoff}
            ORDER BY id
            """)
    List<Reimbursement> selectExpiredDeleted(@Param("cutoff") java.time.LocalDateTime cutoff);

    /** 彻底清除（物理删除，仅限已软删且过保留期的行） */
    @Delete("""
            <script>
            DELETE FROM reimbursement WHERE deleted = 1 AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int purgeByIds(@Param("ids") List<Long> ids);

    /** 当日最大编号（含已软删除行：软删行仍占用唯一键，max 若剔除会导致新单号撞号） */
    @Select("SELECT MAX(reimbursement_no) FROM reimbursement WHERE reimbursement_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxNoIncludingDeleted(@Param("prefix") String prefix);

    /** 导出费用明细扁平行（按明细费用日期范围筛选） */
    @Select("""
            <script>
            SELECT r.reimbursement_no, r.applicant_name, p.name AS project_name, r.title,
                   i.category AS item_category, i.amount AS item_amount,
                   i.expense_date AS item_expense_date, i.description AS item_description,
                   i.invoice_number, i.is_vat_invoice,
                   i.invoice_type, i.tax_rate, i.tax_amount,
                   COALESCE(i.project_id, r.project_id) AS item_project_id,
                   CASE r.status
                       WHEN 0 THEN '草稿' WHEN 1 THEN '待审批' WHEN 2 THEN '已批准'
                       WHEN 3 THEN '已驳回' WHEN 4 THEN '待终审' ELSE '未知' END AS status_label,
                   r.approver_name
            FROM reimbursement r
            JOIN reimbursement_item i ON i.reimbursement_id = r.id
            LEFT JOIN project p ON p.id = COALESCE(i.project_id, r.project_id)
            <where>
                r.deleted = 0
                <if test="startDate != null">AND i.expense_date &gt;= #{startDate}</if>
                <if test="endDate != null">AND i.expense_date &lt;= #{endDate}</if>
            </where>
            ORDER BY i.expense_date DESC, r.id DESC
            </script>
            """)

    List<ReimbursementExportVO> selectExportItems(@Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);
}
