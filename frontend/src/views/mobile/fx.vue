<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getBocRates, getBocHistory } from '@/api/exchangeRate'
import type { ExchangeRateRow } from '@/types'

/** 手机端汇率：当日中行牌价 + 按日期查历史中间价 */
const loading = ref(false)
const keyword = ref('')
const rates = ref<ExchangeRateRow[]>([])

const historyDate = ref('')
const historyLoading = ref(false)
const historyRows = ref<{ currencyName: string; pair: string; rate: string; date: string }[]>([])
const historyQueried = ref(false)

const filtered = computed(() =>
  rates.value.filter((r) => !keyword.value || r.currencyName.includes(keyword.value)),
)

async function fetchRates(): Promise<void> {
  loading.value = true
  try {
    rates.value = await getBocRates()
  } finally {
    loading.value = false
  }
}

async function fetchHistory(): Promise<void> {
  if (!historyDate.value) return
  historyLoading.value = true
  try {
    historyRows.value = await getBocHistory(historyDate.value)
    historyQueried.value = true
  } finally {
    historyLoading.value = false
  }
}

onMounted(fetchRates)
</script>

<template>
  <div class="mx-page">
    <div class="mx-header"><span class="mx-title">汇率牌价</span></div>

    <!-- 当日中行牌价 -->
    <div class="mx-search">
      <el-input v-model="keyword" placeholder="币种筛选，如 美元" clearable />
    </div>

    <div v-loading="loading">
      <div v-for="r in filtered" :key="r.currencyName" class="mx-card">
        <div class="mx-card-head">
          <span class="mx-name">{{ r.currencyName }}</span>
          <span class="mx-mid">折算价 {{ r.bocRate }}</span>
        </div>
        <div class="mx-grid">
          <div class="mx-cell"><span>现汇买入</span>{{ r.spotBuy }}</div>
          <div class="mx-cell"><span>现钞买入</span>{{ r.cashBuy }}</div>
          <div class="mx-cell"><span>现汇卖出</span>{{ r.spotSell }}</div>
          <div class="mx-cell"><span>现钞卖出</span>{{ r.cashSell }}</div>
        </div>
        <div class="mx-time">发布时间：{{ r.publishTime || '—' }}</div>
      </div>
      <div v-if="!loading && !filtered.length" class="mx-empty">暂无牌价数据</div>
    </div>

    <!-- 历史中间价 -->
    <div class="mx-his-title">历史中间价（中国外汇交易中心）</div>
    <div class="mx-search">
      <el-date-picker v-model="historyDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="flex: 1" />
      <el-button type="primary" :loading="historyLoading" @click="fetchHistory">查询</el-button>
    </div>
    <div v-if="historyQueried" class="mx-card">
      <div class="mx-his-date">{{ historyDate }}</div>
      <div v-for="h in historyRows" :key="h.pair" class="mx-his-row">
        <span>{{ h.currencyName }}</span><b>{{ h.rate }}</b>
      </div>
      <div v-if="!historyRows.length" class="mx-empty">该日期无数据</div>
    </div>
  </div>
</template>

<style scoped>
.mx-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mx-header { margin-bottom: 10px; }
.mx-title { font-size: 18px; font-weight: 600; }
.mx-search { display: flex; gap: 8px; margin-bottom: 10px; }
.mx-search .el-input { flex: 1; }
.mx-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mx-card-head { display: flex; justify-content: space-between; align-items: center; }
.mx-name { font-weight: 600; font-size: 14px; }
.mx-mid { color: #2563eb; font-weight: 600; font-size: 13px; }
.mx-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 8px; }
.mx-cell { font-size: 13px; color: #374151; }
.mx-cell span { color: #9ca3af; margin-right: 8px; font-size: 12px; }
.mx-time { color: #c0c4cc; font-size: 11px; margin-top: 8px; }
.mx-his-title { font-size: 13px; color: #9ca3af; margin: 14px 0 8px; }
.mx-his-date { font-weight: 600; font-size: 13px; margin-bottom: 6px; }
.mx-his-row { display: flex; justify-content: space-between; font-size: 13px; color: #374151; padding: 4px 0; border-bottom: 1px solid #f9fafb; }
.mx-empty { text-align: center; color: #9ca3af; padding: 24px 0; font-size: 13px; }
</style>
