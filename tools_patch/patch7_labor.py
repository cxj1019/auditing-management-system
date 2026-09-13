import io

# ===== LaborRateItem DTO =====
open(r'backend/src/main/java/com/accounting/firm/cost/dto/LaborRateItem.java', 'w', encoding='utf-8', newline='\n').write(
"""package com.accounting.firm.cost.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 工时单价保存项 */
@Data
public class LaborRateItem {

    private Long userId;

    private BigDecimal hourlyRate;
}
""")

# ===== ProjectProfitVO：autoLaborCost + budgetHours/actualHours 字段 =====
p = r'backend/src/main/java/com/accounting/firm/cost/dto/ProjectProfitVO.java'
s = io.open(p, encoding='utf-8').read()
if 'autoLaborCost' not in s:
    s = s.replace(
"""    private BigDecimal laborCost;""",
"""    private BigDecimal laborCost;

    /** 工时自动人工成本（推算工时 × 人员单价），已并入 laborCost */
    private BigDecimal autoLaborCost;

    /** 预算工时 */
    private BigDecimal budgetHours;

    /** 实际投入工时（当年推算） */
    private BigDecimal actualHours;""")
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('dto/vo ok')

# ===== CostAnalysisService 接口 =====
p = r'backend/src/main/java/com/accounting/firm/cost/service/CostAnalysisService.java'
s = io.open(p, encoding='utf-8').read()
if 'laborRates' not in s:
    s = s.replace(
"""    /** 人员工时明细""",
"""    /** 工时单价清单（全员，含未设置的默认 0） */
    List<java.util.Map<String, Object>> laborRates();

    /** 保存工时单价（系统管理员） */
    void saveLaborRates(List<com.accounting.firm.cost.dto.LaborRateItem> rates, String operator);

    /** 人员工时明细""", 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('cost service interface ok')

# ===== CostAnalysisController：labor-rates 端点 =====
p = r'backend/src/main/java/com/accounting/firm/cost/controller/CostAnalysisController.java'
s = io.open(p, encoding='utf-8').read()
if 'labor-rates' not in s:
    s = s.replace(
"""    @PreAuthorize("hasAuthority('business:cost:list')")
    @GetMapping("/labor")""",
"""    /** 工时单价清单（成本分析可见者可查） */
    @PreAuthorize("hasAuthority('business:cost:list')")
    @GetMapping("/labor-rates")
    public ApiResult<List<java.util.Map<String, Object>>> laborRates() {
        return ApiResult.success(costAnalysisService.laborRates());
    }

    /** 保存工时单价（仅管理员） */
    @PreAuthorize("hasAuthority('business:cost:labor-edit')")
    @PutMapping("/labor-rates")
    public ApiResult<Void> saveLaborRates(@RequestBody List<com.accounting.firm.cost.dto.LaborRateItem> rates,
                                          @AuthenticationPrincipal SecurityUser currentUser) {
        costAnalysisService.saveLaborRates(rates, currentUser.getUsername());
        return ApiResult.success();
    }

    @PreAuthorize("hasAuthority('business:cost:list')")
    @GetMapping("/labor")""", 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('cost controller ok')
