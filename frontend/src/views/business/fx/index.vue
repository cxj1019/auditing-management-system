<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getBocRates, getBocHistory } from '@/api/exchangeRate'

interface Row {
  currencyName: string
  rate: string
  publishTime?: string
  spotBuy?: string
}

const loading = ref(false)
const rows = ref<Row[]>([])
const isHistory = ref(false)
const query = reactive({ date: '' })

const dayStr = (): string => new Date().toISOString().slice(0, 10)

async function loadLatest(): Promise<void> {
  loading.value = true
  isHistory.value = false
  try {
    const rates = await getBocRates()
    rows.value = rates.map((r) => ({
      currencyName: r.currencyName,
      rate: r.bocRate,
      publishTime: r.publishTime,
      spotBuy: r.spotBuy,
    }))
  } finally {
    loading.value = false
  }
}

async function loadHistory(): Promise<void> {
  if (!query.date) {
    ElMessage.warning('请选择日期')
    return
  }
  loading.value = true
  isHistory.value = true
  try {
    const details = await getBocHistory(query.date)
    rows.value = details
      .map((d) => ({ currencyName: d.currencyName, rate: d.rate, publishTime: d.date }))
      .sort((a, b) => a.currencyName.localeCompare(b.currencyName, 'zh-CN'))
  } finally {
    loading.value = false
  }
}

function backToLatest(): void {
  query.date = ''
  loadLatest()
}

onMounted(loadLatest)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-filters">
          <template v-if="!query.date">
            <el-button type="primary" @click="loadLatest">刷新最新牌价</el-button>
            <span class="source-tip">当前展示：中国银行今日外汇牌价（每 100 外币兑人民币）</span>
          </template>
          <template v-else>
            <el-date-picker
              v-model="query.date"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 160px"
              :clearable="false"
            />
            <el-button type="primary" style="margin-left: 8px" @click="loadHistory">按日期查询</el-button>
            <el-button @click="backToLatest">返回最新</el-button>
            <span class="source-tip">数据来源：中国外汇交易中心人民币汇率中间价</span>
          </template>
        </div>
      </div>

      <el-alert
        v-if="isHistory"
        type="info"
        :closable="false"
        show-icon
        title="历史日期展示中国外汇交易中心人民币汇率中间价；当日实时牌价请返回最新视图查看中国银行外汇牌价"
        style="margin-bottom: 12px"
      />

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="currencyName" label="货币" min-width="140" />
        <el-table-column label="现汇买入价" min-width="120" align="right">
          <template #default="{ row }">{{ row.spotBuy || '—' }}</template>
        </el-table-column>
        <el-table-column label="折算价 / 中间价" min-width="140" align="right">
          <template #default="{ row }">{{ row.rate }}</template>
        </el-table-column>
        <el-table-column label="数据日期" min-width="170">
          <template #default="{ row }">{{ row.publishTime || '—' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.toolbar-filters {
  display: flex;
  align-items: center;
}

.source-tip {
  font-size: 12px;
  color: #9ca3af;
  margin-left: 12px;
}
</style>
