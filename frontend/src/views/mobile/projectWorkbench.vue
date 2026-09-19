<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/api/request'

/** 手机端项目工作台轻量版：指标卡 + 预算/发票/收款/函证/报销 只读列表 */
const route = useRoute()
const router = useRouter()
const pid = Number(route.params.id)

const loading = ref(false)
const wb = ref<any>(null)

const cfStatusLabels: Record<number, string> = { 0: '未发出', 1: '已发出', 2: '已回函', 3: '已作废' }

function money(v?: number | null): string {
  return v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

const cards = computed(() => [
  { label: '合同总额', value: money(wb.value?.contractAmount), color: '#1f2937' },
  { label: '收入（不含税）', value: money(wb.value?.totalCollected), color: '#2563eb' },
  { label: '直接成本', value: money(wb.value?.expenseCost), color: '#e6a23c' },
  { label: '人工合计', value: money(wb.value?.laborCost), color: '#e6a23c' },
  { label: '毛利', value: money(wb.value?.grossProfit), color: Number(wb.value?.grossProfit) >= 0 ? '#67c23a' : '#f56c6c' },
  { label: '实际/预算工时', value: `${Number(wb.value?.actualHours || 0).toFixed(1)}/${Number(wb.value?.budgetHours || 0).toFixed(0)}`, color: '#6b7280' },
])

async function fetchWb(): Promise<void> {
  loading.value = true
  try {
    wb.value = await request.get(`/projects/${pid}/workbench`)
  } finally {
    loading.value = false
  }
}

onMounted(fetchWb)
</script>

<template>
  <div class="mp-page">
    <div class="mp-header">
      <span class="mp-title">项目工作台</span>
      <el-button size="small" text @click="router.back()">返回</el-button>
    </div>

    <div v-loading="loading">
      <div class="mp-info">
        <div class="mp-name">{{ wb?.projectName || '...' }}</div>
        <div class="mp-sub">{{ wb?.projectNo }} · {{ wb?.clientName || '—' }} · {{ wb?.managerName || '—' }}</div>
      </div>

      <div class="mp-cards">
        <div v-for="c in cards" :key="c.label" class="mp-card">
          <div class="mp-value" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="mp-label">{{ c.label }}</div>
        </div>
      </div>

      <div class="mp-sec">发票</div>
      <div class="mp-list">
        <div v-for="inv in wb?.invoices || []" :key="inv.id" class="mp-row">
          <span>{{ inv.invoiceNo || '待开票' }} · {{ inv.type }}</span>
          <b>¥ {{ money(inv.amount) }}</b>
        </div>
        <div v-if="!(wb?.invoices || []).length" class="mp-none">暂无</div>
      </div>

      <div class="mp-sec">收款</div>
      <div class="mp-list">
        <div v-for="p in wb?.payments || []" :key="p.id" class="mp-row">
          <span>{{ p.paymentDate }} · {{ p.paymentMethod }}</span>
          <b>¥ {{ money(p.amount) }}</b>
        </div>
        <div v-if="!(wb?.payments || []).length" class="mp-none">暂无</div>
      </div>

      <div class="mp-sec">函证</div>
      <div class="mp-list">
        <div v-for="cf in wb?.confirmations || []" :key="cf.id" class="mp-row">
          <span>{{ cf.targetUnit }} · {{ cfStatusLabels[cf.status] }}</span>
          <span>{{ cf.sentDate || '—' }}</span>
        </div>
        <div v-if="!(wb?.confirmations || []).length" class="mp-none">暂无</div>
      </div>

      <div class="mp-sec">已批准报销</div>
      <div class="mp-list">
        <div v-for="r in wb?.reimbursements || []" :key="r.reimbursementNo + r.category" class="mp-row">
          <span>{{ r.category }} · {{ r.reimbursementNo }}</span>
          <b>¥ {{ money(r.amountExTax) }}</b>
        </div>
        <div v-if="!(wb?.reimbursements || []).length" class="mp-none">暂无</div>
      </div>

      <div class="mp-sec">对公付款</div>
      <div class="mp-list" style="margin-bottom: 12px">
        <div v-for="v in wb?.vendorPayments || []" :key="v.paymentNo" class="mp-row">
          <span>{{ v.vendorName }} · {{ v.paymentDate }}</span>
          <b>¥ {{ money(v.amountExTax) }}</b>
        </div>
        <div v-if="!(wb?.vendorPayments || []).length" class="mp-none">暂无</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mp-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mp-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.mp-title { font-size: 18px; font-weight: 600; }
.mp-info { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mp-name { font-weight: 600; font-size: 15px; }
.mp-sub { color: #6b7280; font-size: 12px; margin-top: 4px; }
.mp-cards { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-bottom: 12px; }
.mp-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 10px 12px; }
.mp-value { font-size: 16px; font-weight: 600; color: #1f2937; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mp-label { font-size: 11px; color: #9ca3af; margin-top: 3px; }
.mp-sec { font-size: 13px; font-weight: 600; color: #374151; margin: 12px 0 6px; }
.mp-list { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 4px 12px; }
.mp-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 13px; color: #374151; padding: 8px 0; border-bottom: 1px solid #f9fafb; }
.mp-row:last-child { border-bottom: none; }
.mp-none { color: #9ca3af; font-size: 12px; padding: 8px 0; }
</style>
