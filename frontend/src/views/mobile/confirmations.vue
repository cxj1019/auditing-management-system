<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageConfirmations } from '@/api/confirmation'
import type { ConfirmationItem } from '@/types'

/** 手机端函证列表：状态筛选 + 卡片 */
const statusLabels: Record<number, string> = { 0: '未发出', 1: '已发出', 2: '已回函', 3: '已作废' }
const statusTypes: Record<number, 'info' | 'primary' | 'success' | 'danger'> = {
  0: 'info', 1: 'primary', 2: 'success', 3: 'danger',
}

const statusFilters = [
  { label: '全部', value: undefined as number | undefined },
  { label: '未发出', value: 0 },
  { label: '已发出', value: 1 },
  { label: '已回函', value: 2 },
]

const loading = ref(false)
const keyword = ref('')
const activeStatus = ref<number | undefined>(undefined)
const records = ref<ConfirmationItem[]>([])
const expandedId = ref<number | null>(null)

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageConfirmations({
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
  <div class="mf-page">
    <div class="mf-header"><span class="mf-title">函证</span></div>

    <div class="mf-search">
      <el-input v-model="keyword" placeholder="函证号/单位" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div class="mf-chips">
      <span
        v-for="f in statusFilters"
        :key="f.label"
        class="mf-chip"
        :class="{ active: activeStatus === f.value }"
        @click="switchStatus(f.value)"
      >{{ f.label }}</span>
    </div>

    <div v-if="!loading && !records.length" class="mf-empty">没有找到函证</div>

    <div v-for="c in records" :key="c.id" class="mf-card">
      <div class="mf-card-head" @click="expandedId = expandedId === c.id ? null : c.id">
        <span class="mf-name">{{ c.targetUnit }}</span>
        <el-tag :type="statusTypes[c.status]" size="small">{{ statusLabels[c.status] }}</el-tag>
      </div>
      <div class="mf-sub">
        {{ c.confirmationNo }} · {{ c.type }}<el-tag v-if="c.overdue" type="danger" size="small" style="margin-left: 6px">逾期</el-tag>
        <el-tag v-if="c.replyMatched === false" type="warning" size="small" style="margin-left: 6px">回函不符</el-tag>
      </div>

      <div v-if="expandedId === c.id" class="mf-detail">
        <div class="mf-row"><span>函证方式</span>{{ c.confirmationMethod || '—' }}</div>
        <div class="mf-row"><span>函证内容</span>{{ c.summary || '—' }}</div>
        <div class="mf-row"><span>关项目</span>{{ c.projectName || '—' }}</div>
        <div class="mf-row"><span>发出日期</span>{{ c.sentDate || '—' }}</div>
        <div class="mf-row" v-if="c.sendTrackingNo"><span>发出快递</span>{{ c.sendTrackingNo }}</div>
        <div class="mf-row"><span>回函日期</span>{{ c.confirmedDate || '—' }}</div>
        <div class="mf-row" v-if="c.replyTrackingNo"><span>回函快递</span>{{ c.replyTrackingNo }}</div>
        <div class="mf-row" v-if="c.discrepancyReason"><span>差异原因</span>{{ c.discrepancyReason }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mf-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mf-header { margin-bottom: 10px; }
.mf-title { font-size: 18px; font-weight: 600; }
.mf-search { display: flex; gap: 8px; margin-bottom: 10px; }
.mf-search .el-input { flex: 1; }
.mf-chips { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; margin-bottom: 10px; }
.mf-chip { flex-shrink: 0; padding: 4px 12px; border-radius: 999px; background: #fff; border: 1px solid #e5e7eb; font-size: 12px; color: #6b7280; cursor: pointer; }
.mf-chip.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.mf-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mf-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.mf-name { font-weight: 600; font-size: 14px; }
.mf-sub { color: #6b7280; font-size: 12px; margin-top: 5px; display: flex; align-items: center; flex-wrap: wrap; }
.mf-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.mf-row { font-size: 12px; color: #374151; padding: 2px 0; word-break: break-all; }
.mf-row span { display: inline-block; width: 68px; color: #9ca3af; }
.mf-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
