<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageProjects } from '@/api/project'
import type { ProjectItem } from '@/types'

/** 手机端项目列表：状态筛选 + 卡片 + 展开详情 */
const statusLabels: Record<number, string> = { 0: '进行中', 1: '已完成', 2: '已归档' }
const statusTypes: Record<number, 'primary' | 'success' | 'info'> = { 0: 'primary', 1: 'success', 2: 'info' }

const statusFilters = [
  { label: '全部', value: undefined as number | undefined },
  { label: '进行中', value: 0 },
  { label: '已完成', value: 1 },
  { label: '已归档', value: 2 },
]

const loading = ref(false)
const keyword = ref('')
const activeStatus = ref<number | undefined>(undefined)
const records = ref<ProjectItem[]>([])
const expandedId = ref<number | null>(null)

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageProjects({
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

function money(v?: number): string {
  return v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

onMounted(fetchList)
</script>

<template>
  <div class="mp-page">
    <div class="mp-header"><span class="mp-title">项目</span></div>

    <div class="mp-search">
      <el-input v-model="keyword" placeholder="项目名称/编号" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div class="mp-chips">
      <span
        v-for="f in statusFilters"
        :key="f.label"
        class="mp-chip"
        :class="{ active: activeStatus === f.value }"
        @click="switchStatus(f.value)"
      >{{ f.label }}</span>
    </div>

    <div v-if="!loading && !records.length" class="mp-empty">没有找到项目</div>

    <div v-for="p in records" :key="p.id" class="mp-card">
      <div class="mp-card-head" @click="expandedId = expandedId === p.id ? null : p.id">
        <span class="mp-name">{{ p.name }}</span>
        <el-tag :type="statusTypes[p.status]" size="small">{{ statusLabels[p.status] }}</el-tag>
      </div>
      <div class="mp-sub" @click="expandedId = expandedId === p.id ? null : p.id">
        {{ p.projectNo }} · {{ p.clientName || '—' }} · {{ p.type }}
      </div>

      <div v-if="expandedId === p.id" class="mp-detail">
        <div class="mp-row"><span>项目合伙人</span>{{ p.partnerName || '—' }}</div>
        <div class="mp-row"><span>项目负责人</span>{{ p.managerName || '—' }}</div>
        <div class="mp-row"><span>现场负责人</span>{{ p.siteLeaderName || '—' }}</div>
        <div class="mp-row"><span>归属部门</span>{{ p.deptName || '—' }}</div>
        <div class="mp-row"><span>项目期间</span>{{ p.startDate || '—' }} ~ {{ p.endDate || '—' }}</div>
        <div class="mp-row" v-if="p.reportNo"><span>报告文号</span>{{ p.reportNo }}（{{ p.reportDate || '' }}）</div>
        <div class="mp-row" v-if="p.remark"><span>备注</span>{{ p.remark }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mp-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mp-header { margin-bottom: 10px; }
.mp-title { font-size: 18px; font-weight: 600; }
.mp-search { display: flex; gap: 8px; margin-bottom: 10px; }
.mp-search .el-input { flex: 1; }
.mp-chips { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; margin-bottom: 10px; }
.mp-chip { flex-shrink: 0; padding: 4px 12px; border-radius: 999px; background: #fff; border: 1px solid #e5e7eb; font-size: 12px; color: #6b7280; cursor: pointer; }
.mp-chip.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.mp-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mp-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.mp-name { font-weight: 600; font-size: 14px; }
.mp-sub { color: #6b7280; font-size: 12px; margin-top: 5px; }
.mp-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.mp-row { font-size: 12px; color: #374151; padding: 2px 0; }
.mp-row span { display: inline-block; width: 76px; color: #9ca3af; }
.mp-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
