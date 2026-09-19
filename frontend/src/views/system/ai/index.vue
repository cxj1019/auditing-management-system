<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiSettings, saveAiSettings } from '@/api/ai'

const loading = ref(false)
const saving = ref(false)
const form = reactive({ baseUrl: '', apiKey: '', model: '' })
const configured = ref(false)

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

onMounted(fetchSettings)
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
  </div>
</template>
