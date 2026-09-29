package com.accounting.firm.vendor.mapper;

import com.accounting.firm.vendor.entity.VendorPayment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 对公付款 Mapper */
public interface VendorPaymentMapper extends BaseMapper<VendorPayment> {

    /** 年度最大付款编号（含已软删除行：软删行仍占用唯一键，max 若剔除会导致新单号撞号） */
    @Select("SELECT MAX(payment_no) FROM vendor_payment WHERE payment_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxNoIncludingDeleted(@Param("prefix") String prefix);

    /** 回收站：已软删除的付款单（绕过 @TableLogic） */
    @Select("SELECT * FROM vendor_payment WHERE deleted = 1 ORDER BY update_time DESC")
    List<VendorPayment> selectDeleted();

    @Update("UPDATE vendor_payment SET deleted = 0 WHERE id = #{id}")
    int restoreById(@Param("id") Long id);
}
