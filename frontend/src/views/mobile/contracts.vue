<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageContracts } from '@/api/contract'
import type { ContractItem } from '@/types'

/** 手机端合同列表：状态筛选 + 按客户搜索 + 卡片 */
const statusLabels: Record<number, string> = { 0: '草稿', 1: '执行中', 2: '已完成', 3: '已终止' }
const statusTypes: Record<number, 'info' | 'primary' | 'success' | 'danger'> = {
  0: 'info', 1: 'primary', 2: 'success', 3: 'danger',
}

const statusFilters = [
  { label: '全部', value: undefined as number | undefined },
  { label: '执行中', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已终止', value: 3 },
  { label: '草稿', value: 0 },
]

const loading = ref(false)
const keyword = ref('')
const activeStatus = ref<number | undefined>(undefined)
const records = ref<ContractItem[]>([])
const expandedId = ref<number | null>(null)

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageContracts({
      current: 1, size: 50,
      status: activeStatus.value,
      clientName: keyword.value || undefined,
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

function money(v?: number): string {
  return v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

onMounted(fetchList)
</script>

<template>
  <div class="mt-page">
    <div class="mt-header"><span class="mt-title">合同</span></div>

    <div class="mt-search">
      <el-input v-model="keyword" placeholder="客户名称" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div class="mt-chips">
      <span
        v-for="f in statusFilters"
        :key="f.label"
        class="mt-chip"
        :class="{ active: activeStatus === f.value }"
        @click="switchStatus(f.value)"
      >{{ f.label }}</span>
    </div>

    <div v-if="!loading && !records.length" class="mt-empty">没有找到合同</div>

    <div v-for="c in records" :key="c.id" class="mt-card">
      <div class="mt-card-head" @click="expandedId = expandedId === c.id ? null : c.id">
        <span class="mt-name">{{ c.clientName || c.contractNo }}</span>
        <el-tag :type="statusTypes[c.status]" size="small">{{ statusLabels[c.status] }}</el-tag>
      </div>
      <div class="mt-sub" @click="expandedId = expandedId === c.id ? null : c.id">
        {{ c.contractNo }}
      </div>
      <div class="mt-amount">
        <b>¥ {{ money(c.amount) }}</b>
        <span class="mt-amount-sub">签约 {{ c.signDate || '—' }}</span>
      </div>

      <div v-if="expandedId === c.id" class="mt-detail">
        <div class="mt-row"><span>关联合同类型</span>{{ c.contractType || '—' }}</div>
        <div class="mt-row"><span>关联项目</span>{{ c.projectNo || '—' }} {{ c.projectName || '' }}</div>
        <div class="mt-row"><span>不含税金额</span>{{ money(c.amountExTax) }}</div>
        <div class="mt-row"><span>税率 / 税额</span>{{ c.taxRate != null ? c.taxRate + '%' : '—' }} / {{ money(c.taxAmount) }}</div>
        <div class="mt-row" v-if="c.currency && c.currency !== 'CNY'"><span>币种</span>{{ c.currency }} {{ c.foreignAmount ?? '' }} @ {{ c.exchangeRate ?? '' }}</div>
        <div class="mt-row"><span>服务期间</span>{{ c.serviceStart || '—' }} ~ {{ c.serviceEnd || '—' }}</div>
        <div class="mt-row"><span>经办人</span>{{ c.keeperName || '—' }}</div>
        <div class="mt-row" v-if="c.remark"><span>备注</span>{{ c.remark }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mt-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mt-header { margin-bottom: 10px; }
.mt-title { font-size: 18px; font-weight: 600; }
.mt-search { display: flex; gap: 8px; margin-bottom: 10px; }
.mt-search .el-input { flex: 1; }
.mt-chips { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; margin-bottom: 10px; }
.mt-chip { flex-shrink: 0; padding: 4px 12px; border-radius: 999px; background: #fff; border: 1px solid #e5e7eb; font-size: 12px; color: #6b7280; cursor: pointer; }
.mt-chip.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.mt-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mt-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.mt-name { font-weight: 600; font-size: 14px; }
.mt-sub { color: #9ca3af; font-size: 12px; margin-top: 4px; }
.mt-amount { margin-top: 6px; display: flex; justify-content: space-between; align-items: baseline; }
.mt-amount b { color: #f56c6c; font-size: 15px; }
.mt-amount-sub { color: #9ca3af; font-size: 11px; }
.mt-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.mt-row { font-size: 12px; color: #374151; padding: 2px 0; word-break: break-all; }
.mt-row span { display: inline-block; width: 90px; color: #9ca3af; }
.mt-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
