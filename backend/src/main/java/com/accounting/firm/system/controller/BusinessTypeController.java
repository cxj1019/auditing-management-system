package com.accounting.firm.system.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.system.entity.BusinessType;
import com.accounting.firm.system.mapper.BusinessTypeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务类型字典接口（项目性质/项目类型/业务类型/字号/开票要素）
 */
@RestController
@RequestMapping("/api/business-types")
@RequiredArgsConstructor
public class BusinessTypeController {

    private final BusinessTypeMapper businessTypeMapper;

    /** 全量字典（按配置顺序排序；可按项目性质过滤，供项目/合同表单级联选择） */
    @GetMapping
    public ApiResult<List<BusinessType>> list(@RequestParam(required = false) String bizNature) {
        return ApiResult.success(businessTypeMapper.selectList(
                new LambdaQueryWrapper<BusinessType>()
                        .eq(bizNature != null && !bizNature.isEmpty(), BusinessType::getBizNature, bizNature)
                        .orderByAsc(BusinessType::getSort)));
    }

    /** 新增字典项 */
    @AuditLog("新增业务类型")
    @PreAuthorize("hasAuthority('system:dict:add')")
    @PostMapping
    public ApiResult<Void> create(@RequestBody BusinessType businessType) {
        validate(businessType);
        businessType.setId(null);
        businessTypeMapper.insert(businessType);
        return ApiResult.success();
    }

    /** 编辑字典项 */
    @AuditLog("编辑业务类型")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody BusinessType businessType) {
        if (businessType.getId() == null || businessTypeMapper.selectById(businessType.getId()) == null) {
            throw new BusinessException("字典项不存在");
        }
        validate(businessType);
        businessTypeMapper.updateById(businessType);
        return ApiResult.success();
    }

    /** 删除字典项 */
    @AuditLog("删除业务类型")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        if (businessTypeMapper.selectById(id) == null) {
            throw new BusinessException("字典项不存在");
        }
        businessTypeMapper.deleteById(id);
        return ApiResult.success();
    }

    /** 校验必填与（项目类型+业务类型）唯一 */
    private void validate(BusinessType businessType) {
        if (!StringUtils.hasText(businessType.getBizNature())
                || !StringUtils.hasText(businessType.getProjectType())
                || !StringUtils.hasText(businessType.getBizType())) {
            throw new BusinessException("项目性质/项目类型/业务类型均不能为空");
        }
        Long count = businessTypeMapper.selectCount(new LambdaQueryWrapper<BusinessType>()
                .eq(BusinessType::getProjectType, businessType.getProjectType())
                .eq(BusinessType::getBizType, businessType.getBizType())
                .ne(businessType.getId() != null, BusinessType::getId, businessType.getId()));
        if (count > 0) {
            throw new BusinessException("该项目类型下已存在同名业务类型");
        }
    }
}
