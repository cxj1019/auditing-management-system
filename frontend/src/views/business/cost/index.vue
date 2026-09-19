<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import {
  getProjectProfit,
  getCostOverview,
  getProjectHours,
  pageLaborCosts,
  addLaborCost,
  updateLaborCost,
  deleteLaborCost,
} from '@/api/cost'
import { getProjectHourDetails, getLaborRates, saveUserLevels, getStaffLevels, saveStaffLevels } from '@/api/cost'
import { getMonthlyTrend } from '@/api/dashboard'
import { useUserStore } from '@/stores/user'
import { pageProjects } from '@/api/project'
import type { CostOverview, ProjectHoursItem, ProjectItem, ProjectProfitItem } from '@/types'

const activeTab = ref('profit')

// ---------- 项目年份筛选 ----------
const currentYear = new Date().getFullYear()
const yearOptions = [currentYear + 1, currentYear, currentYear - 1, currentYear - 2]
const profitYear = ref<number | undefined>(undefined)

// ---------- 经营概览 ----------
const overview = ref<CostOverview | null>(null)

interface MonthlyRow { ym: string; income: number; expense: number; labor: number }

/** 月度经营报表导出：12 个月收入/成本/人工/毛利 + 全年合计 */
async function exportMonthlyReport(): Promise<void> {
      const y = profitYear.value || new Date().getFullYear()
      const rows: MonthlyRow[] = ((await getMonthlyTrend()) as MonthlyRow[]).filter((m) => String(m.ym).startsWith(String(y)))
      if (!rows.length) {
        ElMessage.info(`${y} 年暂无月度数据`)
        return
      }
      const header = ['月份', '收入（不含税，元）', '报销+对公成本（元）', '人工成本（元）', '当月毛利（元）']
      const body: (string | number)[][] = rows.map((m) => [
        m.ym,
        Number(m.income || 0), Number(m.expense || 0), Number(m.labor || 0),
        Math.round((Number(m.income || 0) - Number(m.expense || 0) - Number(m.labor || 0)) * 100) / 100,
      ])
      const t = (i: number) => Math.round(body.reduce((s: number, r) => s + Number(r[i] || 0), 0) * 100) / 100
      body.push(['全年合计', t(1), t(2), t(3), Math.round((t(1) - t(2) - t(3)) * 100) / 100])

      const ws = XLSX.utils.aoa_to_sheet([header, ...body])
      ws['!cols'] = [{ wch: 10 }, { wch: 18 }, { wch: 20 }, { wch: 16 }, { wch: 16 }]
      const wb = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(wb, ws, '月度经营报表')
      XLSX.writeFile(wb, `月度经营报表_${y}.xlsx`)
      ElMessage.success('月度经营报表已导出')
    }

async function fetchOverview(): Promise<void> {
  overview.value = await getCostOverview()
}

function money(v: number | null | undefined): string {
  if (v === null || v === undefined) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

const overviewCards = computed(() => [
  { title: '总收入（不含税，元）', value: money(overview.value?.totalIncome), color: '#2563eb' },
  { title: '总直接成本（不含税，元）', value: money(overview.value?.totalCost), color: '#e6a23c' },
  { title: '总毛利（元）', value: money(overview.value?.grossProfit), color: (overview.value?.grossProfit ?? 0) >= 0 ? '#67c23a' : '#f56c6c' },
  { title: '整体回款率', value: overview.value?.collectionRate != null ? `${overview.value.collectionRate}%` : '—', color: '#8b5cf6' },
])

// ---------- 项目利润表 ----------
const profitLoading = ref(false)
const userStore = useUserStore()
const canEditRates = computed(() => userStore.hasPermission('business:cost:labor-edit'))
const ratesVisible = ref(false)
const ratesLoading = ref(false)
const ratesSaving = ref(false)
const ratesList = ref<{ userId: number; userName: string; staffLevelId?: number | null; effectiveRate?: number }[]>([])
const levelList = ref<{ id?: number; name: string; hourlyRate: number; sort?: number }[]>([])

async function openRates(): Promise<void> {
  ratesVisible.value = true
  ratesLoading.value = true
  try {
    const [rates, levels] = await Promise.all([getLaborRates(), getStaffLevels()])
    ratesList.value = rates
    levelList.value = levels
  } finally {
    ratesLoading.value = false
  }
}

async function handleSaveRates(): Promise<void> {
  const named = levelList.value.filter((l) => l.name && l.name.trim())
  if (!named.length) {
    ElMessage.warning('至少保留一个有名称的级别')
    return
  }
  ratesSaving.value = true
  try {
    await saveStaffLevels(named)
    await saveUserLevels(ratesList.value.map((r) => ({ userId: r.userId, staffLevelId: r.staffLevelId ?? null })))
    ElMessage.success('级别单价与成员定级已保存，人工成本已按新单价重算')
    ratesVisible.value = false
    fetchProfit()
  } finally {
    ratesSaving.value = false
  }
}
const profitRows = ref<ProjectProfitItem[]>([])
const profitKeyword = ref('')

/** 导出收入成本明细 + 人员工时明细（两个 Sheet） */
const exportingHours = ref(false)

async function handleExportHours(): Promise<void> {
  exportingHours.value = true
  try {
    const kw = profitKeyword.value || undefined
    const y = profitYear.value
    // Sheet1: 收入成本明细(项目维度)
    const profits = await getProjectProfit(kw, y)
    // Sheet2: 人员工时明细(项目 × 人员)
    const hourRows = await getProjectHourDetails(kw, y)
    if (!profits.length && !hourRows.length) {
      ElMessage.info('当前筛选条件下没有数据')
      return
    }
    const wb = XLSX.utils.book_new()

    const profitHeader = ['项目编号', '项目名称', '客户', '合同金额（元）', '收入（不含税）', '直接成本（不含税）', '工时人工-自动（元）', '人工合计（元）', '实际工时（h）', '预算工时（h）', '毛利（元）', '毛利率（%）']
    const profitData = profits.map((r) => [
      r.projectNo, r.projectName, r.clientName || '',
      Number(r.contractAmount || 0), Number(r.totalCollected || 0),
      Number(r.expenseCost || 0), Number(r.autoLaborCost || 0), Number(r.laborCost || 0),
      Number(r.actualHours || 0), r.budgetHours != null ? Number(r.budgetHours) : '',
      Number(r.grossProfit || 0), r.marginPercent ?? '',
    ])
    const ws1 = XLSX.utils.aoa_to_sheet([profitHeader, ...profitData])
    ws1['!cols'] = [{ wch: 18 }, { wch: 28 }, { wch: 18 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 12 }, { wch: 12 }, { wch: 14 }, { wch: 10 }]
    XLSX.utils.book_append_sheet(wb, ws1, '收入成本明细')

    const hourHeader = ['项目编号', '项目名称', '客户', '人员', '工时（小时）']
    const hourData = hourRows.map((r) => [
      r.projectNo, r.projectName, r.clientName || '', r.memberName, Number(r.totalHours),
    ])
    const ws2 = XLSX.utils.aoa_to_sheet([hourHeader, ...hourData])
    ws2['!cols'] = [{ wch: 18 }, { wch: 28 }, { wch: 18 }, { wch: 12 }, { wch: 14 }]
    XLSX.utils.book_append_sheet(wb, ws2, '人员工时明细')

    const tag = y ? `_${y}年` : ''
    XLSX.writeFile(wb, `收入成本及工时明细${tag}_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success(`已导出 ${profits.length} 个项目的收入成本明细与 ${hourRows.length} 条人员工时明细`)
  } finally {
    exportingHours.value = false
  }
}

async function fetchProfit(): Promise<void> {
  profitLoading.value = true
  try {
    profitRows.value = await getProjectProfit(profitKeyword.value || undefined, profitYear.value)
  } finally {
    profitLoading.value = false
  }
}

function marginColor(percent: number): string {
  if (percent >= 50) return '#67c23a'
  if (percent >= 20) return '#409eff'
  return '#e6a23c'
}

// ---------- 人工成本 ----------
const laborLoading = ref(false)

onMounted(() => {
  fetchOverview()
  fetchProfit()
})
</script>

<template>
  <div class="page-container">
    <!-- 概览统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in overviewCards" :key="card.title" :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-title">{{ card.title }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="fetchProfit">
        <!-- 页签一：项目利润 -->
        <el-tab-pane label="项目利润" name="profit">
          <div class="table-toolbar">
            <span class="section-title">按合同维度的收入-成本-毛利</span>
            <div>
              <el-select v-model="profitYear" placeholder="项目年份" clearable style="width: 130px" @change="fetchProfit">
                <el-option v-for="y in yearOptions" :key="y" :label="y + '年'" :value="y" />
              </el-select>
              <el-input v-model="profitKeyword" placeholder="项目编号/名称/客户" clearable style="width: 200px; margin-left: 8px" @keyup.enter="fetchProfit" />
              <el-button type="primary" style="margin-left: 8px" @click="fetchProfit">查询</el-button>
              <el-button v-if="canEditRates" style="margin-left: 8px" @click="openRates">工时单价</el-button>
              <el-button style="margin-left: 8px" @click="exportMonthlyReport">月度经营报表</el-button>
              <el-button :loading="exportingHours" type="success" style="margin-left: 8px" @click="handleExportHours">导出收入成本及工时明细</el-button>
            </div>
          </div>

          <el-table v-loading="profitLoading" :data="profitRows" border stripe>
            <el-table-column prop="projectNo" label="项目编号" min-width="150" />
            <el-table-column prop="projectName" label="项目名称" min-width="170" show-overflow-tooltip />
            <el-table-column label="合同总额（元）" min-width="120" align="right">
              <template #default="{ row }">{{ money(row.contractAmount) }}</template>
            </el-table-column>
            <el-table-column label="收入（不含税）" min-width="120" align="right">
              <template #default="{ row }">{{ money(row.totalCollected) }}</template>
            </el-table-column>
            <el-table-column label="直接成本（不含税）" min-width="130" align="right">
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
            <el-table-column label="毛利（元）" min-width="110" align="right">
              <template #default="{ row }">
                <span :style="{ color: Number(row.grossProfit) >= 0 ? '#67c23a' : '#f56c6c' }">{{ money(row.grossProfit) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="毛利率" min-width="150">
              <template #default="{ row }">
                <template v-if="row.marginPercent !== null && row.marginPercent !== undefined">
                  <el-progress :percentage="Math.min(Math.abs(Number(row.marginPercent)), 100)" :color="marginColor(Number(row.marginPercent))" />
                  <span class="progress-text">{{ row.marginPercent }}%</span>
                </template>
                <span v-else style="color: #9ca3af">—</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 页签二：人工成本 -->
      </el-tabs>
    </el-card>

    <!-- 人工成本弹窗 -->
        <!-- 工时单价：级别标准 + 成员定级 -->
    <el-dialog v-model="ratesVisible" title="工时单价（按级别）" width="640px">
      <p style="margin: 0 0 8px; color: #6b7280; font-size: 13px">
        项目人工成本 = 推算工时 × 成员级别标准单价（个人单价可作个别调整）。级别名称与单价由管理员在此维护。
      </p>

      <div class="section-title" style="margin-bottom: 6px">级别标准单价</div>
      <el-table :data="levelList" border size="small" max-height="220">
        <el-table-column label="级别名称" width="200">
          <template #default="{ row }">
            <el-input v-model="row.name" size="small" maxlength="20" placeholder="如 A1" />
          </template>
        </el-table-column>
        <el-table-column label="标准单价（元/小时）" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.hourlyRate" size="small" :min="0" :max="9999" :precision="2" :controls="false" style="width: 140px" />
          </template>
        </el-table-column>
        <el-table-column label="" width="70">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="levelList.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button size="small" style="margin: 6px 0 14px" @click="levelList.push({ name: '', hourlyRate: 0, sort: (levelList.length + 1) * 10 })">＋ 新增级别</el-button>

      <div class="section-title" style="margin-bottom: 6px">成员定级</div>
      <el-table v-loading="ratesLoading" :data="ratesList" border size="small" max-height="260">
        <el-table-column prop="userName" label="成员" min-width="110" />
        <el-table-column label="级别" width="180">
          <template #default="{ row }">
            <el-select v-model="row.staffLevelId" size="small" clearable placeholder="未定级" style="width: 100%">
              <el-option v-for="lv in levelList" :key="lv.id || lv.name" :label="lv.name" :value="lv.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="生效单价（元/h）" align="right" width="140">
          <template #default="{ row }">{{ Number(row.effectiveRate || 0).toFixed(2) }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="ratesVisible = false">取消</el-button>
        <el-button type="primary" :loading="ratesSaving" @click="handleSaveRates">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
}

.stat-title {
  font-size: 13px;
  color: #6b7280;
  margin-top: 4px;
}

.section-title {
  font-size: 15px;
  font-weight: 500;
  color: #1f2937;
}

.progress-text {
  font-size: 12px;
  color: #6b7280;
  margin-left: 4px;
}
</style>
