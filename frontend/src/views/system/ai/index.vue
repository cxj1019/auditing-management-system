<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiSettings, saveAiSettings } from '@/api/ai'
import request from '@/api/request'

const loading = ref(false)
const saving = ref(false)
const form = reactive({ baseUrl: '', apiKey: '', model: '' })
const apiKeyMasked = ref('')
const configured = ref(false)

interface BackupRow { id: number; objectPath: string; sizeBytes: number; fileCount: number; createTime: string }

const backupRunning = ref(false)
const backupHistory = ref<BackupRow[]>([])
const backupLoading = ref(false)

// ---- 邮件通知设置 ----
interface MailSettings { host?: string; port?: string; username?: string; passwordMasked?: string; from?: string; enabled?: boolean; ready?: boolean }
const mailForm = reactive({ host: '', port: '465', username: '', password: '', from: '', enabled: false })
const mailReady = ref(false)
const mailSaving = ref(false)
const mailTesting = ref(false)
const testMailTo = ref('')

async function fetchMailSettings(): Promise<void> {
  try {
    const d = await request.get('/system/mail-settings') as unknown as MailSettings
    mailForm.host = d.host || ''
    mailForm.port = d.port || '465'
    mailForm.username = d.username || ''
    mailForm.password = ''
    mailForm.from = d.from || ''
    mailForm.enabled = !!d.enabled
    mailReady.value = !!d.ready
  } catch { /* 无权限时不显示 */ }
}

async function handleSaveMail(): Promise<void> {
  if (!mailForm.host.trim() || !mailForm.username.trim() || !mailForm.from.trim()) {
    ElMessage.warning('请填写 SMTP 服务器、账号和发件人')
    return
  }
  mailSaving.value = true
  try {
    await request.put('/system/mail-settings', {
      host: mailForm.host, port: mailForm.port, username: mailForm.username,
      password: mailForm.password, from: mailForm.from, enabled: mailForm.enabled ? 'true' : 'false',
    })
    ElMessage.success('邮件设置已保存')
    fetchMailSettings()
  } finally {
    mailSaving.value = false
  }
}

async function handleTestMail(): Promise<void> {
  if (!testMailTo.value.trim()) {
    ElMessage.warning('请填写测试收件邮箱')
    return
  }
  mailTesting.value = true
  try {
    await request.post('/system/mail-settings/test', { to: testMailTo.value })
    ElMessage.success('测试邮件已发送，请查收')
  } finally {
    mailTesting.value = false
  }
}

// ---- 回收站/审计日志清理 ----
const cleanupRunning = ref(false)
async function handleRetentionRun(): Promise<void> {
  cleanupRunning.value = true
  try {
    const msg = await request.post('/system/retention/run') as unknown as string
    ElMessage.success(msg || '清理完成')
  } finally {
    cleanupRunning.value = false
  }
}

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
    form.apiKey = ''
    apiKeyMasked.value = (data.apiKeyMasked as string) || ''
    form.model = (data.model as string) || ''
    configured.value = !!data.configured
  } finally {
    loading.value = false
  }
}

async function handleSave(): Promise<void> {
  if (!form.baseUrl.trim() || !form.model.trim()) {
    ElMessage.warning('请完整填写接口地址和模型名')
    return
  }
  if (!apiKeyMasked.value && !form.apiKey.trim()) {
    ElMessage.warning('请填写 API Key')
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
      fetchMailSettings()
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
        <el-form-item label="API Key" :required="!apiKeyMasked">
          <el-input v-model="form.apiKey" type="password" show-password :placeholder="apiKeyMasked ? `已保存 ${apiKeyMasked}，留空保持不变` : 'sk-...'" />
        </el-form-item>
        <el-form-item label="模型" required>
          <el-input v-model="form.model" placeholder="如 gemini-3.6-flash / glm-4.6v / qwen-vl-plus（需支持图片输入）" />
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
          <span>邮件通知设置（SMTP，审批/提醒将同步发邮件）</span>
          <el-tag :type="mailReady ? 'success' : 'info'" size="small">{{ mailReady ? '已就绪' : '未启用' }}</el-tag>
        </div>
      </template>
      <el-form label-width="110px">
        <el-form-item label="启用">
          <el-switch v-model="mailForm.enabled" />
        </el-form-item>
        <el-form-item label="SMTP 服务器" required>
          <el-input v-model="mailForm.host" placeholder="如 smtp.exmail.qq.com" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input v-model="mailForm.port" placeholder="SSL 端口一般为 465" />
        </el-form-item>
        <el-form-item label="SMTP 账号" required>
          <el-input v-model="mailForm.username" placeholder="发件邮箱账号" />
        </el-form-item>
        <el-form-item label="SMTP 密码">
          <el-input v-model="mailForm.password" type="password" show-password placeholder="已保存则留空保持不变" />
        </el-form-item>
        <el-form-item label="发件人" required>
          <el-input v-model="mailForm.from" placeholder="与 SMTP 账号一致，如 system@firm.cn" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="mailSaving" @click="handleSaveMail">保存</el-button>
          <el-input v-model="testMailTo" placeholder="测试收件邮箱" style="width: 220px; margin-left: 12px" />
          <el-button :loading="mailTesting" style="margin-left: 8px" @click="handleTestMail">发送测试邮件</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon
        title="说明" description="员工账号名即邮箱（如 wangjp@mytscpa.com）时才会收到邮件提醒；测试账号（firm.cn 假邮箱）不会发送。" />
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
      <div style="margin-top: 10px; display: flex; align-items: center; gap: 8px">
        <el-button size="small" :loading="cleanupRunning" @click="handleRetentionRun">立即执行保留策略清理</el-button>
        <span style="color: #9ca3af; font-size: 12px">审计日志保留 180 天；回收站单据保留 30 天后连同附件彻底清除（每日 02:40 自动）</span>
      </div>
    </el-card>
  </div>
</template>
