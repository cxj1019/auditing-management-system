<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiSettings, saveAiSettings } from '@/api/ai'
import request from '@/api/request'

const loading = ref(false)
const saving = ref(false)
const form = reactive({ baseUrl: '', apiKey: '', model: '' })
const configured = ref(false)

interface BackupRow { id: number; objectPath: string; sizeBytes: number; fileCount: number; createTime: string }

const backupRunning = ref(false)
const backupHistory = ref<BackupRow[]>([])
const backupLoading = ref(false)

async function fetchBackupHistory(): Promise<void> {
      backupLoading.value = true
      try {
        backupHistory.value = await request.get('/system/backup/history') as unknown as BackupRow[]
      } finally {
        backupLoading.value = false
      }
    }

async function handleRunBackup(): Promise<void> {
      backupRunning.value = true
      try {
        const r = await request.post('/system/backup/run') as unknown as BackupRow
        ElMessage.success(`备份完成：${(r.sizeBytes / 1024 / 1024).toFixed(2)} MB，${r.fileCount} 个附件`)
        fetchBackupHistory()
      } finally {
        backupRunning.value = false
      }
    }

function fmtSize(b?: number): string {
      if (!b) return '—'
      if (b > 1024 * 1024) return (b / 1024 / 1024).toFixed(2) + ' MB'
      return (b / 1024).toFixed(0) + ' KB'
    }

function fmtTime(t?: string): string {
      return t ? t.replace('T', ' ').slice(0, 16) : '—'
    }

async function fetchSettings(): Promise<void> {
  loading.value = true
  try {
    const data = await getAiSettings()
    form.baseUrl = (data.baseUrl as string) || ''
    form.apiKey = (data.apiKey as string) || ''
    form.model = (data.model as string) || ''
    configured.value = !!data.configured
  } finally {
    loading.value = false
  }
}

async function handleSave(): Promise<void> {
  if (!form.baseUrl.trim() || !form.apiKey.trim() || !form.model.trim()) {
    ElMessage.warning('请完整填写接口地址、API Key 和模型名')
    return
  }
  saving.value = true
  try {
    await saveAiSettings({ baseUrl: form.baseUrl, apiKey: form.apiKey, model: form.model })
    ElMessage.success('AI 设置已保存')
    fetchSettings()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
      fetchSettings()
      fetchBackupHistory()
    })
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" style="max-width: 680px">
      <template #header>
        <span>AI 设置（OpenAI 兼容接口）</span>
      </template>
      <el-form v-loading="loading" label-width="110px">
        <el-form-item label="接口地址" required>
          <el-input v-model="form.baseUrl" placeholder="如 https://api.openai.com/v1 或 https://dashscope.aliyuncs.com/compatible-mode/v1" />
        </el-form-item>
        <el-form-item label="API Key" required>
          <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="模型" required>
          <el-input v-model="form.model" placeholder="如 gpt-4o-mini / qwen-vl-plus / deepseek-chat" />
        </el-form-item>
        <el-form-item>
          <el-tag v-if="configured" type="success" size="small">已配置（函证智能导入可用）</el-tag>
          <el-tag v-else type="info" size="small">未配置（函证智能导入不可用）</el-tag>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon
        title="用途说明" description="函证管理的「智能批量导入」会把扫描 PDF 按页发给该视觉模型识别被函证单位，请选择支持图片输入的模型。" />
    </el-card>

    <el-card shadow="never" style="max-width: 680px; margin-top: 12px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>数据备份（全部数据 + 全部附件，每日 02:30 自动）</span>
          <el-button type="primary" size="small" :loading="backupRunning" @click="handleRunBackup">立即备份</el-button>
        </div>
      </template>
      <p style="margin: 0 0 8px; color: #6b7280; font-size: 13px">
        备份为完整 ZIP（表数据 JSON + 全部附件原文件），仅保留最近 7 天的每日备份。
      </p>
      <el-table v-loading="backupLoading" :data="backupHistory" border size="small" max-height="300">
        <el-table-column label="备份时间" min-width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ fmtSize(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column label="附件数" width="80" align="center">
          <template #default="{ row }">{{ row.fileCount }}</template>
        </el-table-column>
        <el-table-column label="文件" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.objectPath }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
