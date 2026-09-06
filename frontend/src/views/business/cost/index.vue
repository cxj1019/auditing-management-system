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
import { getProjectHourDetails } from '@/api/cost'
import { pageProjects } from '@/api/project'
import type { CostOverview, LaborCostItem, LaborCostRequest, ProjectHoursItem, ProjectItem, ProjectProfitItem } from '@/types'

const activeTab = ref('profit')

// ---------- 项目年份筛选 ----------
const currentYear = new Date().getFullYear()
const yearOptions = [currentYear + 1, currentYear, currentYear - 1, currentYear - 2]
const profitYear = ref<number | undefined>(undefined)

// ---------- 经营概览 ----------
const overview = ref<CostOverview | null>(null)

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

    const profitHeader = ['项目编号', '项目名称', '客户', '合同金额（元）', '收入（不含税）', '直接成本（不含税）', '人工成本（元）', '毛利（元）', '毛利率（%）']
    const profitData = profits.map((r) => [
      r.projectNo, r.projectName, r.clientName || '',
      Number(r.contractAmount || 0), Number(r.totalCollected || 0),
      Number(r.expenseCost || 0), Number(r.laborCost || 0),
      Number(r.grossProfit || 0), r.marginPercent ?? '',
    ])
    const ws1 = XLSX.utils.aoa_to_sheet([profitHeader, ...profitData])
    ws1['!cols'] = [{ wch: 18 }, { wch: 28 }, { wch: 18 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 10 }]
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
const laborRows = ref<LaborCostItem[]>([])
const laborTotal = ref(0)
const laborQuery = reactive({ current: 1, size: 10 })

async function fetchLabor(): Promise<void> {
  laborLoading.value = true
  try {
    const data = await pageLaborCosts(laborQuery)
    laborRows.value = data.records
    laborTotal.value = data.total
  } finally {
    laborLoading.value = false
  }
}

const laborDialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<LaborCostRequest>({
  projectId: 0,
  personName: '',
  costMonth: '',
  amount: 0,
  remark: '',
})
const projectOptions = ref<ProjectItem[]>([])

async function loadProjectOptions(): Promise<void> {
  const data = await pageProjects({ current: 1, size: 200 })
  projectOptions.value = data.records
}

function openCreate(): void {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, { projectId: undefined, personName: '', costMonth: '', amount: 0, remark: '' })
  loadProjectOptions()
  laborDialogVisible.value = true
}

function openEdit(row: LaborCostItem): void {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    projectId: row.projectId,
    personName: row.personName,
    costMonth: row.costMonth,
    amount: row.amount,
    remark: row.remark,
  })
  laborDialogVisible.value = true
}

/** 编辑时合同不可更换 */
async function handleSave(): Promise<void> {
  saving.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateLaborCost(editingId.value, form)
      ElMessage.success('修改成功')
    } else {
      await addLaborCost(form)
      ElMessage.success('登记成功')
    }
    laborDialogVisible.value = false
    fetchLabor()
    fetchProfit()
    fetchOverview()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: LaborCostItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除「${row.personName} ${row.costMonth}」的人工成本吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteLaborCost(row.id)
    ElMessage.success('删除成功')
    fetchLabor()
    fetchProfit()
    fetchOverview()
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  fetchOverview()
  fetchProfit()
  fetchLabor()
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
        <el-tab-pane label="人工成本" name="labor">
          <div class="table-toolbar">
            <span class="section-title">项目人工投入登记</span>
            <el-button v-permission="'business:cost:labor-add'" type="primary" @click="openCreate">登记人工成本</el-button>
          </div>

          <el-table v-loading="laborLoading" :data="laborRows" border stripe>
            <el-table-column prop="projectId" label="项目 ID" width="90" />
            <el-table-column prop="personName" label="人员" min-width="110" />
            <el-table-column prop="costMonth" label="成本月份" width="110" />
            <el-table-column label="金额（元）" min-width="120" align="right">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'business:cost:labor-edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button v-permission="'business:cost:labor-delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="laborQuery.current"
              v-model:page-size="laborQuery.size"
              :total="laborTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchLabor"
              @size-change="fetchLabor"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 人工成本弹窗 -->
    <el-dialog v-model="laborDialogVisible" :title="isEdit ? '编辑人工成本' : '登记人工成本'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="所属项目" required>
          <el-select v-model="form.projectId" :disabled="isEdit" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="p in projectOptions" :key="p.id" :label="`${p.projectNo} | ${p.name}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="人员姓名" required>
          <el-input v-model="form.personName" placeholder="人员姓名" maxlength="50" />
        </el-form-item>
        <el-form-item label="成本月份" required>
          <el-date-picker v-model="form.costMonth" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 100%" />
        </el-form-item>
        <el-form-item label="金额（元）" required>
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="1000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="laborDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
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
