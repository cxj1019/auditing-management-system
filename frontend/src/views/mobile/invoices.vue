<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageInvoices } from '@/api/invoice'
import type { InvoiceItem } from '@/types'

/** 手机端发票列表：状态筛选 + 卡片 */
const statusLabels: Record<number, string> = { 0: '待开票', 1: '已开票', 2: '已作废' }
const statusTypes: Record<number, 'info' | 'success' | 'danger'> = { 0: 'info', 1: 'success', 2: 'danger' }

const statusFilters = [
  { label: '全部', value: undefined as number | undefined },
  { label: '待开票', value: 0 },
  { label: '已开票', value: 1 },
  { label: '已作废', value: 2 },
]

const loading = ref(false)
const keyword = ref('')
const activeStatus = ref<number | undefined>(undefined)
const records = ref<InvoiceItem[]>([])
const expandedId = ref<number | null>(null)

function money(v?: number): string {
  return v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageInvoices({
      current: 1, size: 50,
      status: activeStatus.value,
      keyword: keyword.value || undefined,
    })
    records.value = data.records
  } finally {
    loading.value = false
  }
}

function switchStatus(v: number | undefined): void {
  activeStatus.value = v
  expandedId.value = null
  fetchList()
}

onMounted(fetchList)
</script>

<template>
  <div class="mi-page">
    <div class="mi-header"><span class="mi-title">发票</span></div>

    <div class="mi-search">
      <el-input v-model="keyword" placeholder="发票号/客户/合同" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div class="mi-chips">
      <span
        v-for="f in statusFilters"
        :key="f.label"
        class="mi-chip"
        :class="{ active: activeStatus === f.value }"
        @click="switchStatus(f.value)"
      >{{ f.label }}</span>
    </div>

    <div v-if="!loading && !records.length" class="mi-empty">没有找到发票</div>

    <div v-for="inv in records" :key="inv.id" class="mi-card">
      <div class="mi-card-head" @click="expandedId = expandedId === inv.id ? null : inv.id">
        <span class="mi-name">{{ inv.clientName || inv.contractNo }}</span>
        <el-tag :type="statusTypes[inv.status]" size="small">{{ statusLabels[inv.status] }}</el-tag>
      </div>
      <div class="mi-sub">
        {{ inv.invoiceNo || '待开票' }} · {{ inv.type }}
        <el-tag v-if="inv.isRecharge" type="warning" size="small" style="margin-left: 6px">垫付</el-tag>
      </div>
      <div class="mi-amount">
        <b>¥ {{ money(inv.amount) }}</b>
        <span class="mi-amount-sub">已收 {{ money(inv.collectedAmount) }}</span>
      </div>

      <div v-if="expandedId === inv.id" class="mi-detail">
        <div class="mi-row"><span>关联合同</span>{{ inv.contractNo }} {{ inv.contractName || '' }}</div>
        <div class="mi-row" v-if="inv.projectNo"><span>项目</span>{{ inv.projectNo }} {{ inv.projectName || '' }}</div>
        <div class="mi-row"><span>不含税 / 税额</span>{{ money(inv.amountExTax) }} / {{ money(inv.taxAmount) }}</div>
        <div class="mi-row" v-if="inv.taxRate != null"><span>税率</span>{{ inv.taxRate }}%</div>
        <div class="mi-row"><span>开票日期</span>{{ inv.invoiceDate || '—' }}</div>
        <div class="mi-row" v-if="inv.invoiceItem"><span>发票项目</span>{{ inv.invoiceItem }}</div>
        <div class="mi-row" v-if="inv.currency && inv.currency !== 'CNY'"><span>币种</span>{{ inv.currency }} {{ inv.foreignAmount ?? '' }} @ {{ inv.exchangeRate ?? '' }}</div>
        <div class="mi-row" v-if="inv.remark"><span>备注</span>{{ inv.remark }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mi-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mi-header { margin-bottom: 10px; }
.mi-title { font-size: 18px; font-weight: 600; }
.mi-search { display: flex; gap: 8px; margin-bottom: 10px; }
.mi-search .el-input { flex: 1; }
.mi-chips { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; margin-bottom: 10px; }
.mi-chip { flex-shrink: 0; padding: 4px 12px; border-radius: 999px; background: #fff; border: 1px solid #e5e7eb; font-size: 12px; color: #6b7280; cursor: pointer; }
.mi-chip.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.mi-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mi-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.mi-name { font-weight: 600; font-size: 14px; }
.mi-sub { color: #6b7280; font-size: 12px; margin-top: 5px; display: flex; align-items: center; }
.mi-amount { margin-top: 6px; display: flex; justify-content: space-between; align-items: baseline; }
.mi-amount b { color: #f56c6c; font-size: 15px; }
.mi-amount-sub { color: #16a34a; font-size: 11px; }
.mi-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.mi-row { font-size: 12px; color: #374151; padding: 2px 0; word-break: break-all; }
.mi-row span { display: inline-block; width: 92px; color: #9ca3af; }
.mi-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
