package com.accounting.firm.vendor.mapper;

import com.accounting.firm.vendor.entity.VendorPayment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 对公付款 Mapper */
public interface VendorPaymentMapper extends BaseMapper<VendorPayment> {

    /** 年度最大付款编号（含已软删除行：软删行仍占用唯一键，max 若剔除会导致新单号撞号） */
    @Select("SELECT MAX(payment_no) FROM vendor_payment WHERE payment_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxNoIncludingDeleted(@Param("prefix") String prefix);
}
