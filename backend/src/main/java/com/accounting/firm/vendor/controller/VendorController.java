package com.accounting.firm.vendor.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.vendor.entity.Vendor;
import com.accounting.firm.vendor.mapper.VendorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** 供应商主数据：付款登记下拉选择，按供应商统计口径统一 */
@RestController
@RequestMapping("/api/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorMapper vendorMapper;
    private final com.accounting.firm.vendor.mapper.VendorPaymentMapper vendorPaymentMapper;

    @PreAuthorize("hasAuthority('business:vendor:list')")
    @GetMapping
    public ApiResult<List<Vendor>> list(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Vendor> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Vendor::getVendorName, keyword)
                    .or().like(Vendor::getContact, keyword)
                    .or().like(Vendor::getTaxNo, keyword));
        }
        wrapper.orderByAsc(Vendor::getVendorName);
        return ApiResult.success(vendorMapper.selectList(wrapper));
    }

    @PreAuthorize("hasAuthority('business:vendor:add')")
    @PostMapping
    public ApiResult<Long> create(@RequestBody Vendor vendor, @AuthenticationPrincipal SecurityUser currentUser) {
        requireName(vendor);
        if (nameExists(vendor.getVendorName(), null)) {
            throw new BusinessException("供应商名称已存在");
        }
        vendor.setId(null);
        vendor.setCreateBy(currentUser.getUsername());
        vendor.setCreateTime(LocalDateTime.now());
        vendor.setUpdateTime(LocalDateTime.now());
        vendorMapper.insert(vendor);
        return ApiResult.success(vendor.getId());
    }

    @PreAuthorize("hasAuthority('business:vendor:edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody Vendor vendor) {
        if (vendor.getId() == null) {
            throw new BusinessException("供应商 ID 不能为空");
        }
        requireName(vendor);
        if (nameExists(vendor.getVendorName(), vendor.getId())) {
            throw new BusinessException("供应商名称已存在");
        }
        vendor.setUpdateTime(LocalDateTime.now());
        vendorMapper.updateById(vendor);
        return ApiResult.success();
    }

    @PreAuthorize("hasAuthority('business:vendor:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        Vendor vendor = vendorMapper.selectById(id);
        if (vendor == null) {
            throw new BusinessException("供应商不存在");
        }
        Long refs = vendorPaymentMapper.selectCount(new LambdaQueryWrapper<com.accounting.firm.vendor.entity.VendorPayment>()
                .eq(com.accounting.firm.vendor.entity.VendorPayment::getVendorName, vendor.getVendorName()));
        if (refs != null && refs > 0) {
            throw new BusinessException("该供应商已有 " + refs + " 笔付款记录，不可删除");
        }
        vendorMapper.deleteById(id);
        return ApiResult.success();
    }

    private void requireName(Vendor vendor) {
        if (vendor.getVendorName() == null || vendor.getVendorName().isBlank()) {
            throw new BusinessException("供应商名称不能为空");
        }
        vendor.setVendorName(vendor.getVendorName().trim());
    }

    private boolean nameExists(String name, Long excludeId) {
        Long count = vendorMapper.selectCount(new LambdaQueryWrapper<Vendor>()
                .eq(Vendor::getVendorName, name)
                .ne(excludeId != null, Vendor::getId, excludeId));
        return count != null && count > 0;
    }
}
