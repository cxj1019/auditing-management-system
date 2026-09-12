<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getCollectionSummary } from '@/api/collection'
import type { CollectionSummaryItem } from '@/types'

/** 手机端收款：合同维度汇总卡片（合同金额/已收/未核销/计划） */
const loading = ref(false)
const keyword = ref('')
const records = ref<CollectionSummaryItem[]>([])
const expandedId = ref<number | null>(null)

function money(v?: number): string {
  return v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    records.value = await getCollectionSummary(keyword.value || undefined)
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="ml-page">
    <div class="ml-header"><span class="ml-title">收款</span></div>

    <div class="ml-search">
      <el-input v-model="keyword" placeholder="合同号/客户/项目" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div v-if="!loading && !records.length" class="ml-empty">暂无收款数据</div>

    <div v-for="(c, i) in records" :key="i" class="ml-card">
      <div class="ml-card-head" @click="expandedId = expandedId === i ? null : i">
        <span class="ml-name">{{ c.clientName || c.contractNo }}</span>
        <span class="ml-amount">¥ {{ money(c.totalCollected) }}</span>
      </div>
      <div class="ml-sub">{{ c.contractNo }}<template v-if="c.contractName"> · {{ c.contractName }}</template></div>

      <div v-if="expandedId === i" class="ml-detail">
        <div class="ml-row"><span>合同金额</span>{{ money(c.contractAmount) }}</div>
        <div class="ml-row"><span>已回款</span>{{ money(c.totalCollected) }}</div>
        <div class="ml-row"><span>未核销</span>{{ money(c.outstanding) }}</div>
        <div class="ml-row" v-if="c.plannedTotal != null"><span>计划回款</span>{{ money(c.plannedTotal) }}</div>
        <div class="ml-row"><span>回款进度</span>{{ (c.progressPercent ?? 0).toFixed(0) }}%</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ml-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.ml-header { margin-bottom: 10px; }
.ml-title { font-size: 18px; font-weight: 600; }
.ml-search { display: flex; gap: 8px; margin-bottom: 10px; }
.ml-search .el-input { flex: 1; }
.ml-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.ml-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.ml-name { font-weight: 600; font-size: 14px; }
.ml-amount { color: #16a34a; font-weight: 600; font-size: 14px; flex-shrink: 0; }
.ml-sub { color: #9ca3af; font-size: 12px; margin-top: 4px; }
.ml-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.ml-row { display: flex; justify-content: space-between; font-size: 13px; color: #374151; padding: 3px 0; }
.ml-row span { color: #9ca3af; }
.ml-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
