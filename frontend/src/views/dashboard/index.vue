<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { useUserStore } from '@/stores/user'
import { getDashboard, getMonthlyTrend, getHealthCheck } from '@/api/dashboard'
import { getExpenseStats } from '@/api/cost'
import request from '@/api/request'
import * as XLSX from 'xlsx'
import type { DashboardSummary, MonthlyTrendItem, HealthCheckItem } from '@/types'

// ---------- 应收账龄 ----------
interface AgingRow { invoiceNo: string; clientName: string; projectName: string; invoiceDate: string; amount: number; paid: number; outstanding: number; days: number; bucket: string }
interface AgingSummary { total: number; b30: number; b60: number; b90: number; b90plus: number }
const agingRows = ref<AgingRow[]>([])
const agingSummary = ref<AgingSummary | null>(null)
const agingExporting = ref(false)

async function fetchAging(): Promise<void> {
  try {
    const d = await request.get('/dashboard/receivable-aging') as unknown as { rows: AgingRow[]; summary: AgingSummary }
    agingRows.value = d.rows || []
    agingSummary.value = d.summary || null
  } catch { /* 无权限/失败不影响看板 */ }
}

function exportAging(): void {
  agingExporting.value = true
  try {
    const header = ['发票号', '客户', '项目', '开票日期', '账龄天数', '账龄分层', '含税额', '已回款', '未回款']
    const aoa = [header, ...agingRows.value.map((r) => [
      r.invoiceNo, r.clientName, r.projectName, r.invoiceDate, r.days, r.bucket, r.amount, r.paid, r.outstanding,
    ])]
    const ws = XLSX.utils.aoa_to_sheet(aoa)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '应收账龄')
    XLSX.writeFile(wb, `应收账龄_${new Date().toISOString().slice(0, 10)}.xlsx`)
  } finally {
    agingExporting.value = false
  }
}

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
/** 普通员工不展示工时与成本/经营数据 */
const canViewFinance = computed(() => userStore.hasRole('admin') || userStore.hasRole('manager'))
const summary = ref<DashboardSummary>()

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

function money(v?: number): string {
  if (v === undefined || v === null) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 待办卡片定义：数值 + 点击跳转 */
const todoCards = computed(() => {
  const todo = summary.value?.todo
  return [
    { key: 'reimbursement', title: '待审批报销', value: todo?.pendingReimbursement ?? 0, path: '/business/reimbursement', color: '#f56c6c' },
    { key: 'invoice', title: '待开发票', value: todo?.pendingInvoice ?? 0, path: '/business/invoice', color: '#e6a23c' },
    { key: 'receivable', title: '逾期应收', value: todo?.overdueReceivable ?? 0, path: '/business/invoice', color: '#f56c6c' },
    { key: 'confirmation', title: '逾期函证', value: todo?.overdueConfirmation ?? 0, path: '/business/confirmation', color: '#e6a23c' },
    { key: 'contract', title: '合同将到期', value: todo?.expiringContract ?? 0, path: '/business/contract', color: '#8b5cf6' },
  ]
})

function go(path: string): void {
  router.push(path)
}

// ---------- 数据体检 ----------
const healthItems = ref<HealthCheckItem[]>([])

// ---------- 经营趋势(仅管理员/经理) ----------
const trendRef = ref<HTMLElement>()
const pieRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

async function renderCharts(): Promise<void> {
  if (!canViewFinance.value) return
  try {
    const [trend, stats] = await Promise.all([
      getMonthlyTrend().catch(() => [] as MonthlyTrendItem[]),
      getExpenseStats(undefined).catch(() => []),
    ])
    await nextTick()
    if (trendRef.value) {
      trendChart = trendChart || echarts.init(trendRef.value)
      const months = trend.map((t) => t.ym)
      trendChart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['收入', '报销成本', '人工成本'] },
        grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
        xAxis: { type: 'category', data: months },
        yAxis: { type: 'value' },
        series: [
          { name: '收入', type: 'bar', data: trend.map((t) => Number(t.income)) },
          { name: '报销成本', type: 'line', data: trend.map((t) => Number(t.expense)) },
          { name: '人工成本', type: 'line', data: trend.map((t) => Number(t.labor)) },
        ],
      })
    }
    if (pieRef.value) {
      const data = stats.map((s) => ({ name: s.category, value: Number(s.total || 0) }))
        .filter((d) => d.value > 0)
      pieChart = pieChart || echarts.init(pieRef.value)
      pieChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} 元 ({d}%)' },
        legend: { bottom: 0, type: 'scroll' },
        series: [{ type: 'pie', radius: ['38%', '66%'], center: ['50%', '44%'], data }],
      })
    }
  } catch {
    // 图表数据加载失败不影响首页其它内容
  }
}

onMounted(async () => {
  loading.value = true
  try {
    summary.value = await getDashboard()
    fetchAging()
    if (canViewFinance.value) {
      try {
        healthItems.value = await getHealthCheck()
      } catch { /* 忽略 */ }
      await renderCharts()
      window.addEventListener('resize', () => {
        trendChart?.resize()
        pieChart?.resize()
      })
    }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- 欢迎卡片 -->
    <el-card shadow="never" class="welcome-card">
      <div class="welcome-content">
        <div>
          <h2 class="welcome-title">{{ greeting }}，{{ userStore.nickname }}</h2>
          <p class="welcome-desc">欢迎使用会计师事务所管理系统，祝您工作顺利。</p>
        </div>
        <div class="welcome-meta">
          <div v-if="canViewFinance">本周工时：<b class="week-hours">{{ summary?.weekHours ?? 0 }}</b> 小时</div>
          <div>当前账号：{{ userStore.username }}</div>
        </div>
      </div>
    </el-card>

    <!-- 待办卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in todoCards" :key="card.key" :xs="24" :sm="12" :lg="4">
        <el-card shadow="hover" class="stat-card" @click="go(card.path)">
          <div class="stat-value" :style="{ color: card.value > 0 ? card.color : '#9ca3af' }">{{ card.value }}</div>
          <div class="stat-title">{{ card.title }}</div>
        </el-card>
      </el-col>
      <el-col v-if="canViewFinance" :xs="24" :sm="12" :lg="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #2563eb">{{ money(summary?.receivable?.outstanding) }}</div>
          <div class="stat-title">未核销余额（元）</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据体检 -->
    <el-card v-if="healthItems.length" shadow="never" class="health-card">
      <div class="health-list">
        <div v-for="h in healthItems" :key="h.key" class="health-item" @click="go(h.path)">
          <span class="health-count">{{ h.count }}</span>
          <span class="health-title">{{ h.title }}</span>
          <span class="health-arrow">→</span>
        </div>
      </div>
    </el-card>

    <!-- 经营趋势(仅管理员/经理) -->
    <el-row v-if="canViewFinance" :gutter="16">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="block-card">
          <template #header>
            <div class="block-header">
              <span>近 12 个月经营趋势（不含税）</span>
              <el-button link type="primary" @click="go('/business/cost')">成本分析</el-button>
            </div>
          </template>
          <div ref="trendRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="block-card">
          <template #header>
            <div class="block-header"><span>本年报销费用类别占比</span></div>
          </template>
          <div ref="pieRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 今日日程 -->
      <el-col :xs="24" :lg="canViewFinance ? 12 : 24">
        <el-card shadow="never" class="block-card">
          <template #header>
            <div class="block-header">
              <span>今日日程</span>
              <el-button link type="primary" @click="go('/business/schedule')">日程管理</el-button>
            </div>
          </template>
          <template v-if="summary?.todaySchedules?.length">
            <div v-for="s in summary.todaySchedules" :key="s.id" class="schedule-item">
              <div class="schedule-time">{{ s.startTime || '全天' }}<span v-if="s.endTime"> ~ {{ s.endTime }}</span></div>
              <div class="schedule-body">
                <div class="schedule-title">{{ s.title || '(未命名日程)' }}</div>
                <div class="schedule-sub">{{ s.projectName || '未关联项目' }} · {{ s.type }}<span v-if="s.hours"> · {{ s.hours }}h</span></div>
              </div>
            </div>
          </template>
          <el-empty v-else description="今天没有日程安排" :image-size="60" />
        </el-card>
      </el-col>

      <el-col v-if="canViewFinance" :xs="24" :lg="12">
        <!-- 开票与回款 -->
        <el-card shadow="never" class="block-card">
          <template #header>
            <div class="block-header">
              <span>开票与回款</span>
              <el-button link type="primary" @click="go('/business/collection')">收款管理</el-button>
            </div>
          </template>
          <div class="receivable-grid">
            <div class="receivable-cell">
              <div class="receivable-value">{{ money(summary?.receivable?.invoicedAmount) }}</div>
              <div class="receivable-label">已开票（元）</div>
            </div>
            <div class="receivable-cell">
              <div class="receivable-value" style="color: #16a34a">{{ money(summary?.receivable?.collectedAmount) }}</div>
              <div class="receivable-label">已回款（元）</div>
            </div>
            <div class="receivable-cell">
              <div class="receivable-value" style="color: #f56c6c">{{ money(summary?.receivable?.outstanding) }}</div>
              <div class="receivable-label">未核销（元）</div>
            </div>
          </div>
        </el-card>

        <!-- 应收账龄 -->
        <el-card shadow="never" class="block-card">
          <template #header>
            <div class="block-header">
              <span>应收账龄（未回清发票）</span>
              <el-button link type="primary" :loading="agingExporting" @click="exportAging">导出</el-button>
            </div>
          </template>
          <div class="receivable-grid">
            <div class="receivable-cell">
              <div class="receivable-value">{{ money(agingSummary?.b30) }}</div>
              <div class="receivable-label">0-30天（元）</div>
            </div>
            <div class="receivable-cell">
              <div class="receivable-value" style="color: #d97706">{{ money(agingSummary?.b60) }}</div>
              <div class="receivable-label">31-60天（元）</div>
            </div>
            <div class="receivable-cell">
              <div class="receivable-value" style="color: #f56c6c">{{ money(agingSummary?.b90) }}</div>
              <div class="receivable-label">61-90天（元）</div>
            </div>
            <div class="receivable-cell">
              <div class="receivable-value" style="color: #dc2626">{{ money(agingSummary?.b90plus) }}</div>
              <div class="receivable-label">90天以上（元）</div>
            </div>
          </div>
          <el-table v-if="agingRows.length" :data="agingRows.slice(0, 8)" size="small" border style="margin-top: 10px">
            <el-table-column prop="invoiceNo" label="发票号" min-width="120" />
            <el-table-column prop="clientName" label="客户" min-width="120" show-overflow-tooltip />
            <el-table-column prop="invoiceDate" label="开票日" width="100" />
            <el-table-column prop="days" label="账龄" width="70" align="center">
              <template #default="{ row }">{{ row.days }} 天</template>
            </el-table-column>
            <el-table-column label="未回款" min-width="100" align="right">
              <template #default="{ row }">{{ money(row.outstanding) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="agingRows.length > 8" style="color: #9ca3af; font-size: 12px; margin-top: 6px">
            共 {{ agingRows.length }} 张，仅显示最旧 8 张，导出查看全部
          </div>
        </el-card>

        <!-- 项目规模 Top5 -->
        <el-card v-if="canViewFinance" shadow="never" class="block-card">
          <template #header>
            <div class="block-header">
              <span>项目规模 Top5（按合同金额）</span>
              <el-button link type="primary" @click="go('/business/cost')">成本分析</el-button>
            </div>
          </template>
          <template v-if="summary?.topProjects?.length">
            <div v-for="p in summary.topProjects" :key="p.projectNo" class="proj-row">
              <div class="proj-info">
                <div class="proj-name">{{ p.projectNo }} {{ p.projectName }}</div>
                <div class="proj-sub">合同 {{ money(p.contractAmount) }} · 已收 {{ money(p.totalCollected) }}</div>
              </div>
              <el-progress class="proj-progress" :percentage="Math.min(p.progressPercent ?? 0, 100)"
                :color="(p.progressPercent ?? 0) >= 100 ? '#67c23a' : '#409eff'" />
            </div>
          </template>
          <el-empty v-else description="暂无项目数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.welcome-card {
  margin-bottom: 16px;
}

.welcome-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.welcome-title {
  font-size: 20px;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.welcome-desc {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.welcome-meta {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  text-align: right;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.week-hours {
  color: #2563eb;
  font-size: 16px;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: left;
  cursor: pointer;
  margin-bottom: 8px;
}

.stat-card:hover {
  transform: translateY(-2px);
  transition: transform 0.15s;
}

.stat-value {
  font-size: 26px;
  font-weight: 600;
  color: #2563eb;
}

.stat-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.block-card {
  margin-bottom: 16px;
}

.block-header {
  color: var(--el-text-color-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
}

.schedule-item {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.schedule-time {
  flex-shrink: 0;
  width: 110px;
  font-size: 13px;
  color: var(--el-color-primary);
  font-weight: 500;
}

.schedule-title {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.schedule-sub {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 2px;
}

.receivable-grid {
  display: flex;
  text-align: center;
}

.receivable-cell {
  flex: 1;
}

.receivable-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.receivable-label {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 4px;
}

.proj-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 7px 0;
  border-bottom: 1px solid #f9fafb;
}

.proj-name {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.proj-sub {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 2px;
}

.proj-progress {
  width: 160px;
  flex-shrink: 0;
}

.health-card {
  margin-bottom: 16px;
}

.health-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.health-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  border-radius: 8px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 13px;
}

.health-item:hover {
  border-color: #f59e0b;
}

.health-count {
  font-weight: 700;
  color: #d97706;
}

.health-title {
  color: #374151;
}

.health-arrow {
  color: #9ca3af;
}
</style>
