<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  fileName: string
  contentType?: string
  fetchSignedUrl: () => Promise<string>
}>()

const signedUrl = ref<string | null>(null)
const loading = ref(false)

const isImage = computed(() => {
  if (props.contentType) return props.contentType.startsWith('image/')
  return /\.(jpg|jpeg|png|gif)$/i.test(props.fileName)
})

const isPdf = computed(() => {
  if (props.contentType) return props.contentType === 'application/pdf'
  return /\.pdf$/i.test(props.fileName)
})

const isPreviewable = computed(() => isImage.value || isPdf.value)

// 附件切换时清空缓存的签名 URL
watch(() => props.fileName, () => {
  signedUrl.value = null
})

async function ensureUrl(): Promise<void> {
  if (signedUrl.value || loading.value) return
  loading.value = true
  try {
    signedUrl.value = await props.fetchSignedUrl()
  } catch {
    ElMessage.error('获取预览地址失败')
  } finally {
    loading.value = false
  }
}

function openInNewWindow(): void {
  ensureUrl().then(() => {
    if (signedUrl.value) window.open(signedUrl.value, '_blank')
  })
}
</script>

<template>
  <!-- 图片或 PDF：悬停弹出预览 + 点击新窗口 -->
  <el-popover
    v-if="isPreviewable"
    trigger="hover"
    placement="left"
    :width="'auto'"
    :popper-style="{ maxWidth: '600px', minWidth: '300px', padding: '8px' }"
    @show="ensureUrl"
  >
    <template #reference>
      <el-button link type="primary" size="small" @click="openInNewWindow">{{ fileName }}</el-button>
    </template>
    <!-- 图片预览：宽度自适应内容，最大不超过 600px -->
    <div v-if="isImage" style="text-align: center">
      <el-image
        v-if="signedUrl"
        :src="signedUrl"
        :style="{ maxWidth: '580px', maxHeight: '450px' }"
        fit="contain"
      />
      <el-skeleton v-else :rows="4" animated style="width: 300px" />
    </div>
    <!-- PDF 预览：宽高自适应，基于视口尺寸 -->
    <div v-else-if="isPdf" :style="{ width: 'min(580px, 80vw)', height: 'min(420px, 70vh)' }">
      <iframe v-if="signedUrl" :src="signedUrl" style="width: 100%; height: 100%; border: none; border-radius: 4px" />
      <el-skeleton v-else :rows="8" animated style="width: 100%; height: 100%" />
    </div>
  </el-popover>
  <!-- 其他文件（doc 等）：仅点击新窗口下载 -->
  <el-button v-else link type="primary" size="small" @click="openInNewWindow">{{ fileName }}</el-button>
</template>
