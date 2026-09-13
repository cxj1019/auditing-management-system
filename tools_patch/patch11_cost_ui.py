import io
p = r'frontend/src/views/business/cost/index.vue'
s = io.open(p, encoding='utf-8').read()

# 1) imports
s = s.replace(
"""import { getProjectHourDetails, getExpenseStats } from '@/api/cost'""",
"""import { getProjectHourDetails, getExpenseStats, getLaborRates, saveLaborRates } from '@/api/cost'
import { useUserStore } from '@/stores/user'""")

# 2) state：在 profitRows 附近注入（找 profitLoading 声明处）
old = """const profitLoading = ref(false)"""
new = """const profitLoading = ref(false)
const userStore = useUserStore()
const canEditRates = computed(() => userStore.hasPermission('business:cost:labor-edit'))
const ratesVisible = ref(false)
const ratesLoading = ref(false)
const ratesSaving = ref(false)
const ratesList = ref<{ userId: number; userName: string; hourlyRate: number }[]>([])

async function openRates(): Promise<void> {
  ratesVisible.value = true
  ratesLoading.value = true
  try {
    ratesList.value = await getLaborRates()
  } finally {
    ratesLoading.value = false
  }
}

async function handleSaveRates(): Promise<void> {
  ratesSaving.value = true
  try {
    await saveLaborRates(ratesList.value.map((r) => ({ userId: r.userId, hourlyRate: Number(r.hourlyRate) || 0 })))
    ElMessage.success('工时单价已保存，项目人工成本已按新单价重算')
    ratesVisible.value = false
    fetchProfits()
  } finally {
    ratesSaving.value = false
  }
}"""
assert old in s
s = s.replace(old, new, 1)

# 3) 表格列：直接成本后加 工时人工(自动)/人工合计/预算与实际工时
old = """            <el-table-column label="直接成本（不含税）" min-width="130" align="right">
              <template #default="{ row }">{{ money(row.directCost) }}</template>
            </el-table-column>
            <el-table-column label="毛利（元）" min-width="110" align="right">"""
new = """            <el-table-column label="直接成本（不含税）" min-width="130" align="right">
              <template #default="{ row }">{{ money(row.directCost) }}</template>
            </el-table-column>
            <el-table-column label="工时人工（自动）" min-width="120" align="right">
              <template #default="{ row }">{{ money(row.autoLaborCost || 0) }}</template>
            </el-table-column>
            <el-table-column label="人工合计（元）" min-width="120" align="right">
              <template #default="{ row }">{{ money(row.laborCost) }}</template>
            </el-table-column>
            <el-table-column label="工时（实际/预算）" min-width="130" align="right">
              <template #default="{ row }">
                <span :style="row.budgetHours && Number(row.actualHours || 0) > Number(row.budgetHours) ? 'color:#f56c6c' : ''">
                  {{ Number(row.actualHours || 0).toFixed(1) }}{{ row.budgetHours ? ' / ' + Number(row.budgetHours).toFixed(1) : '' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="毛利（元）" min-width="110" align="right">"""
assert old in s
s = s.replace(old, new)

# 4) 导出列补自动人工/实际/预算
old = """    const profitHeader = ['项目编号', '项目名称', '客户', '合同金额（元）', '收入（不含税）', '直接成本（不含税）', '人工成本（元）', '毛利（元）', '毛利率（%）']
    const profitData = profits.map((r) => [
      r.projectNo, r.projectName, r.clientName || '',
      Number(r.contractAmount || 0), Number(r.totalCollected || 0),
      Number(r.expenseCost || 0), Number(r.laborCost || 0),
      Number(r.grossProfit || 0), r.marginPercent ?? '',
    ])
    const ws1 = XLSX.utils.aoa_to_sheet([profitHeader, ...profitData])
    ws1['!cols'] = [{ wch: 18 }, { wch: 28 }, { wch: 18 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 10 }]"""
new = """    const profitHeader = ['项目编号', '项目名称', '客户', '合同金额（元）', '收入（不含税）', '直接成本（不含税）', '工时人工-自动（元）', '人工合计（元）', '实际工时（h）', '预算工时（h）', '毛利（元）', '毛利率（%）']
    const profitData = profits.map((r) => [
      r.projectNo, r.projectName, r.clientName || '',
      Number(r.contractAmount || 0), Number(r.totalCollected || 0),
      Number(r.expenseCost || 0), Number(r.autoLaborCost || 0), Number(r.laborCost || 0),
      Number(r.actualHours || 0), r.budgetHours != null ? Number(r.budgetHours) : '',
      Number(r.grossProfit || 0), r.marginPercent ?? '',
    ])
    const ws1 = XLSX.utils.aoa_to_sheet([profitHeader, ...profitData])
    ws1['!cols'] = [{ wch: 18 }, { wch: 28 }, { wch: 18 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 12 }, { wch: 12 }, { wch: 14 }, { wch: 10 }]"""
assert old in s
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('cost page script/table ok')
