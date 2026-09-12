<script setup lang="ts">
import { ref } from 'vue'
import { compressImage } from '@/utils/imageCompress'

/**
 * 通用附件上传按钮组：「拍照」（调起手机摄像头）+「选择文件」
 * 选中的图片自动压缩（见 imageCompress），通过 pick 事件把 File[] 交给页面逐个上传
 */
const props = defineProps<{
  /** 父组件上传中状态（与本地压缩状态合并控制按钮 loading） */
  uploading?: boolean
  /** 「选择文件」允许的类型，桌面默认与各业务页原有 accept 一致 */
  accept?: string
  /** 「选择文件」按钮文字 */
  text?: string
  /** 是否显示拍照按钮（默认显示） */
  withCamera?: boolean
}>()

const emit = defineEmits<{ (e: 'pick', files: File[]): void }>()

const camInput = ref<HTMLInputElement>()
const fileInput = ref<HTMLInputElement>()
const busy = ref(false)

async function onChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const raw = Array.from(input.files || [])
  input.value = ''
  if (!raw.length) return
  busy.value = true
  try {
    const files: File[] = []
    for (const f of raw) files.push(await compressImage(f))
    emit('pick', files)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <span class="cap-upload">
    <el-button v-if="withCamera !== false" size="small" type="primary" plain :loading="props.uploading || busy" @click="camInput?.click()">
      📷 拍照
    </el-button>
    <el-button size="small" :loading="props.uploading || busy" @click="fileInput?.click()">
      {{ text ?? '选择文件' }}
    </el-button>
    <input ref="camInput" type="file" accept="image/*" capture="environment" multiple hidden @change="onChange">
    <input ref="fileInput" type="file" :accept="accept ?? '.pdf,.jpg,.jpeg,.png'" multiple hidden @change="onChange">
  </span>
</template>
