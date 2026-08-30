<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { pageAuditLogs } from '@/api/audit'
import type { AuditLogItem } from '@/types'

const loading = ref(false)
const records = ref<AuditLogItem[]>([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 20,
  username: '',
  keyword: '',
  dateRange: [] as string[],
})

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageAuditLogs({
      current: query.current,
      size: query.size,
      username: query.username || undefined,
      keyword: query.keyword || undefined,
      startDate: query.dateRange?.[0],
      endDate: query.dateRange?.[1],
    })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  query.current = 1
  fetchList()
}

function handleReset(): void {
  query.username = ''
  query.keyword = ''
  query.dateRange = []
  handleSearch()
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-filters">
          <el-input v-model="query.username" placeholder="操作人账号" clearable style="width: 160px" @keyup.enter="handleSearch" />
          <el-input v-model="query.keyword" placeholder="操作内容" clearable style="width: 180px; margin-left: 8px" @keyup.enter="handleSearch" />
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px; margin-left: 8px"
          />
          <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="操作人" width="140">
          <template #default="{ row }">{{ row.username || 'anonymous' }}</template>
        </el-table-column>
        <el-table-column prop="operation" label="操作内容" min-width="160" />
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.result === '成功' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorMsg || '—' }}</template>
        </el-table-column>
        <el-table-column label="耗时（ms）" width="110" align="right">
          <template #default="{ row }">{{ row.costMs ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="createTime" label="操作时间" width="180">
          <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList"
          @size-change="handleSearch"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.toolbar-filters {
  display: flex;
  align-items: center;
}
</style>
